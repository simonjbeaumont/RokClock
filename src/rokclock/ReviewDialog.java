package rokclock;

import java.awt.*;
import java.awt.datatransfer.*;
import java.awt.event.*;
import java.io.*;
import java.text.*;
import java.util.*;
import java.util.Map.*;

import javax.swing.*;
import javax.swing.event.*;

/**
 * The dialog for reviewing summaries of log entries for a specific time period.
 */
@SuppressWarnings("serial")
class ReviewDialog extends JDialog implements CaretListener {
	/**
	 * A label associated with a date. On user's mouse click, it presents the
	 * user with {@link DateChooser} to change the date.
	 */
	private class DateLabel extends JLabel {
		/**
		 * The calendar that has a date associated with it.
		 */
		private GregorianCalendar calendar = new GregorianCalendar();

		/**
		 * The constructor that defines the basic user interface and the mouse
		 * click handler.
		 */
		DateLabel() {
			super("", CENTER);
			setToolTipText("Click to change.");
			setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
			addMouseListener(new MouseAdapter() {
				@Override
				public void mouseClicked(MouseEvent e) {
					DateChooser dc = new DateChooser(ReviewDialog.this, calendar);
					if (dc.showDateChooser() == DateChooser.OK_OPTION) {
						calendar = dc.getDate();
						resetYearWeekComboBoxes();
						refresh();
						refreshReviewTable();
					}
				}
			});
		}

		/**
		 * A method used to set the date externally (not through {@link DateChooser}).
		 *
		 * @param calendar The calendar to clone.
		 */
		void setDate(GregorianCalendar calendar) {
			this.calendar = (GregorianCalendar) calendar.clone();
			refresh();
		}

		/**
		 * Obtains the date associated with the label.
		 *
		 * @return The date.
		 */
		Date getDate() {
			return calendar.getTime();
		}

		/**
		 * Refreshes the label's text according to the current date.
		 */
		private void refresh() {
			setText(dateFormat.format(calendar.getTime()));
		}
	}

	/**
	 * A row of data on the review dialog. It contains the necessary data as
	 * well as the corresponding GUI components.
	 */
	private class Row {
		/**
		 * The hours associated with the row's top-level project.
		 */
		private double hours = 0;
		/**
		 * The text field to adapt hours computed by the analyser.
		 */
		private JTextField hoursTF = new JTextField();
		/**
		 * The label showing the percentage of hours spent on row's top-level
		 * project.
		 */
		private JLabel percentL = new JLabel("N/A", SwingConstants.RIGHT);

		/**
		 * The constructor sets up the listeners to update values appropriately.
		 */
		Row() {
			hoursTF.addCaretListener(ReviewDialog.this);
			hoursTF.setHorizontalAlignment(SwingConstants.RIGHT);
		}
	}

	/**
	 * The date format used for displaying dates in labels.
	 */
	private final DateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy");
	/**
	 * The decimal format used for displaying hours an percentages.
	 */
	private final DecimalFormat decimalFormat = new DecimalFormat("0.00");
	/**
	 * A link to the parent component.
	 */
	private Main main;
	/**
	 * A link to the configuration object.
	 */
	private Config config;
	/**
	 * A link to the generic log analyser.
	 */
	private Analyser analyser = new Analyser();

	/**
	 * The default colour to display the text.
	 */
	private Color normalColour = Color.BLACK;
	/**
	 * The colour used to display an error.
	 */
	private Color errorColour = Color.RED;

	/**
	 * The label describing the 'from' date label to its right.
	 */
	private JLabel fromLabel = new JLabel("From (inclusive):", SwingConstants.RIGHT);
	/**
	 * The label describing the 'to' date label to its right.
	 */
	private JLabel toLabel = new JLabel("To (exclusive):", SwingConstants.RIGHT);
	/**
	 * The label describing the 'year' combo box to its right.
	 */
	private JLabel yearLabel = new JLabel("Year:", SwingConstants.RIGHT);
	/**
	 * The label describing the 'week' combo box to its right.
	 */
	private JLabel weekLabel = new JLabel("Week:", SwingConstants.RIGHT) {{
		setToolTipText("According to ISO 8601.");
	}};
	/**
	 * The Gregorian calendar used for year-week selection.
	 */
	private GregorianCalendar yearWeekCalendar = new GregorianCalendar() {{
		setMinimalDaysInFirstWeek(4);
	}};
	/**
	 * The combo box used to display the year selection.
	 */
	private JComboBox yearCB = new JComboBox() {{
		addItem("Custom");
		int currentYear = yearWeekCalendar.get(Calendar.YEAR);
		for (int year = currentYear - 5; year <= currentYear + 5; year++)
			addItem(year);
		addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent ae) {
				if (yearCB.getSelectedIndex() == 0) return;
				yearWeekCalendar.set(Calendar.YEAR, (Integer) yearCB.getSelectedItem());
				updateYearWeekDates();
			}
		});
	}};
	/**
	 * The combo box used to display the week selection.
	 */
	private JComboBox weekCB = new JComboBox() {{
		addItem("Custom");
		for (int week = 1; week <= 52; week++)
			addItem(week);
		addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent ae) {
				if (weekCB.getSelectedIndex() == 0) return;
				yearWeekCalendar.set(Calendar.WEEK_OF_YEAR, (Integer) weekCB.getSelectedItem());
				updateYearWeekDates();
			}
		});
	}};
	/**
	 * The 'from' date label.
	 */
	private DateLabel fromDate = new DateLabel();
	/**
	 * The 'to' date label.
	 */
	private DateLabel toDate = new DateLabel();
	/**
	 * The sub-panel containing the rows; each row contains a project name, its
	 * hours, and the percentage.
	 */
	private JPanel reviewPanel = new JPanel();
	/**
	 * A map from top-level project names to corresponding rows.
	 */
	private Map<String, Row> rows = new HashMap<String, Row>();
	/**
	 * The label showing the total number of hours.
	 */
	private JLabel totalLabel = new JLabel("", SwingConstants.RIGHT);
	/**
	 * The button used to save the results into a file.
	 */
	private JButton saveToFileButton = createSaveToFileButton();
	/**
	 * The button used to copy the results into the clipboard.
	 */
	private JButton copyToClipboardButton = createCopyToClipboardButton();
	/**
	 * The file chooser used to choose the file to save to.
	 */
	private JFileChooser fileChooser = new JFileChooser();

	/**
	 * The only constructor of the review dialog, which initialises the dates,
	 * sets the outer layout, runs the analyser, creates rows for the results,
	 * and displays the window.
	 *
	 * @param main
	 *            A link to the parent component.
	 * @param config
	 *            A link to the configuration object.
	 */
	ReviewDialog(Main main, Config config) {
		super(main, "Review & Save");
		this.main = main;
		this.config = config;
		// layout date components
		GridBagLayout gbl = new GridBagLayout();
		setLayout(gbl);
		GridBagConstraints gbc = new GridBagConstraints();
		gbc.fill = GridBagConstraints.BOTH;
		gbc.insets = new Insets(0, 0, 5, 0);
		gbc.ipadx = 10;
		gbc.gridx = 0; gbc.gridy = 0;
		gbl.setConstraints(yearLabel, gbc);
		gbc.gridx = 1;
		gbl.setConstraints(yearCB, gbc);
		gbc.gridx = 0; gbc.gridy = 1;
		gbl.setConstraints(weekLabel, gbc);
		gbc.gridx = 1;
		gbl.setConstraints(weekCB, gbc);
		gbc.gridx = 0; gbc.gridy = 2;
		gbl.setConstraints(fromLabel, gbc);
		gbc.weightx = 1;
		gbc.gridx = 1;
		gbl.setConstraints(fromDate, gbc);
		gbc.insets = new Insets(0, 0, 0, 0);
		gbc.weightx = 0;
		gbc.gridx = 0; gbc.gridy = 3;
		gbl.setConstraints(toLabel, gbc);
		gbc.weightx = 1;
		gbc.gridx = 1;
		gbl.setConstraints(toDate, gbc);
		gbc.gridx = 0; gbc.gridy = 4; gbc.gridwidth = 2;
		gbc.insets = new Insets(10, 5, 10, 5);
		gbc.weighty = 1;
		JScrollPane scrollReviewPanel = new JScrollPane(reviewPanel);
		gbl.setConstraints(scrollReviewPanel, gbc);
		gbc.insets = new Insets(0, 0, 0, 0);
		gbc.gridy = 5; gbc.weighty = 0;
		gbl.setConstraints(saveToFileButton, gbc);
		gbc.gridy = 6;
		gbl.setConstraints(copyToClipboardButton, gbc);
		add(yearLabel);
		add(yearCB);
		add(weekLabel);
		add(weekCB);
		add(fromLabel);
		add(fromDate);
		add(toLabel);
		add(toDate);
		add(scrollReviewPanel);
		add(saveToFileButton);
		add(copyToClipboardButton);
		// layout results
		updateYearWeekDates();
		setVisible(true);
		setLocation(main.getLocation());
	}

	/**
	 * Set calendar to this week's Monday; set year and week combo boxes to the
	 * currently set date; set the date labels appropriately; and, refresh the
	 * review table.
	 */
	private void updateYearWeekDates() {
		yearWeekCalendar.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY);
		yearWeekCalendar.set(Calendar.HOUR_OF_DAY, 0);
		yearWeekCalendar.set(Calendar.MINUTE, 0);
		yearWeekCalendar.set(Calendar.SECOND, 0);
		yearWeekCalendar.set(Calendar.MILLISECOND, 0);
		yearCB.setSelectedItem(yearWeekCalendar.get(Calendar.YEAR));
		weekCB.setSelectedItem(yearWeekCalendar.get(Calendar.WEEK_OF_YEAR));
		fromDate.setDate(yearWeekCalendar);
		yearWeekCalendar.add(Calendar.DAY_OF_MONTH, 7);
		toDate.setDate(yearWeekCalendar);
		yearWeekCalendar.add(Calendar.DAY_OF_MONTH, -7);
		refreshReviewTable();
	}

	/**
	 * Set both year and week combo boxes to field "Custom".
	 */
	private void resetYearWeekComboBoxes() {
		yearCB.setSelectedIndex(0);
		weekCB.setSelectedIndex(0);
	}

	/**
	 * This function is used to re-run the analyser, and re-create the rows
	 * corresponding the its results.
	 */
	private void refreshReviewTable() {
		reviewPanel.removeAll();
		rows.clear();
		GridBagLayout gbl = new GridBagLayout();
		reviewPanel.setLayout(gbl);
		GridBagConstraints gbc = new GridBagConstraints();
		gbc.fill = GridBagConstraints.HORIZONTAL;
		gbc.gridy = 0;
		try {
			Map<String, Long> sums = analyser.processLogFile(config.getLogFilename(), fromDate.getDate(), toDate.getDate());
			for (Entry<String, Long> entry : sums.entrySet()) {
				String project = entry.getKey();
				double hours = 1.0 * entry.getValue() / (1000 * 3600);
				addRow(gbl, gbc, project, hours);
			}
			for (String project : main.getProjectsTree().getTopLevelProjects())
				if (!rows.containsKey(project))
					addRow(gbl, gbc, project, 0);
			gbc.insets = new Insets(10, 0, 0, 0);
			addLeftLabel(gbl, gbc, "TOTAL");
			gbc.gridx = 1;
			gbc.weightx = 1;
			totalLabel.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 3));
			gbl.setConstraints(totalLabel, gbc);
			reviewPanel.add(totalLabel);
			gbc.weightx = 0;
			addRightLabel(gbl, gbc);
		} catch (IOException e) {e.printStackTrace();}
		recomputeTotal();
		pack();
	}

	/**
	 * Adds a single row to the {@link #reviewPanel}.
	 *
	 * @param gbl
	 *            The layout to add the row to.
	 * @param gbc
	 *            The layout constraints to use.
	 * @param title
	 *            The title of the top-level project.
	 * @param hours
	 *            The amount of hours spent on the project.
	 */
	private void addRow(GridBagLayout gbl, GridBagConstraints gbc, String title, double hours) {
		Row row = new Row();
		addLeftLabel(gbl, gbc, title);
		addMiddleField(gbl, gbc, row, hours);
		addRightLabel(gbl, gbc);
		addPercentLabel(gbl, gbc, row);
		rows.put(title, row);
		gbc.gridy++;
	}

	/**
	 * Adds the description part of a row.
	 *
	 * @param gbl
	 *            The layout to add the label to.
	 * @param gbc
	 *            The layout constraints to use.
	 * @param title
	 *            The title of the top-level project.
	 *
	 * @see {@link #addRow(GridBagLayout, GridBagConstraints, String, double)}
	 */
	private void addLeftLabel(GridBagLayout gbl, GridBagConstraints gbc, String title) {
		JLabel projectLabel = new JLabel(title + ": ", SwingConstants.RIGHT);
		gbc.gridx = 0;
		gbl.setConstraints(projectLabel, gbc);
		reviewPanel.add(projectLabel);
	}

	/**
	 * Adds an editable text field containing the hours spent on a project.
	 *
	 * @param gbl
	 *            The layout to add the text field to.
	 * @param gbc
	 *            The layout constraints to use.
	 * @param row
	 *            The row to link against.
	 * @param hours
	 *            The number of hours spent on the project.
	 *
	 * @see {@link #addRow(GridBagLayout, GridBagConstraints, String, double)}
	 */
	private void addMiddleField(GridBagLayout gbl, GridBagConstraints gbc, Row row, double hours) {
		row.hoursTF.setText(decimalFormat.format(hours));
		gbc.gridx = 1;
		gbc.weightx = 1;
		gbl.setConstraints(row.hoursTF, gbc);
		gbc.weightx = 0;
		reviewPanel.add(row.hoursTF);
	}

	/**
	 * Adds a simple 'h' to show that the time period is specified in hours.
	 *
	 * @param gbl
	 *            The layout to add the label to.
	 * @param gbc
	 *            The layout constraints to use.
	 *
	 * @see {@link #addRow(GridBagLayout, GridBagConstraints, String, double)}
	 */
	private void addRightLabel(GridBagLayout gbl, GridBagConstraints gbc) {
		JLabel hLabel = new JLabel("h", SwingConstants.CENTER);
		gbc.gridx = 2;
		gbc.ipadx = 5;
		gbl.setConstraints(hLabel, gbc);
		gbc.ipadx = 0;
		reviewPanel.add(hLabel);
	}

	/**
	 * Adds a label that shows the percentage of hours spent on a particular
	 * project.
	 *
	 * @param gbl
	 *            The layout to add the label to.
	 * @param gbc
	 *            The layout constraints to use.
	 * @param row
	 *            The row to link against.
	 *
	 * @see {@link #addRow(GridBagLayout, GridBagConstraints, String, double)}
	 */
	private void addPercentLabel(GridBagLayout gbl, GridBagConstraints gbc, Row row) {
		gbc.gridx = 3;
		gbc.ipadx = 5;
		gbl.setConstraints(row.percentL, gbc);
		gbc.ipadx = 0;
		reviewPanel.add(row.percentL);
	}

	/**
	 * This function re-computes the total number of hours for the selected
	 * period. It is ran every time the user edits a text field specifying the
	 * number of hours for a particular top-level project. Percentage labels are
	 * also updated.
	 */
	private void recomputeTotal() {
		double total = 0;
		for (Row row : rows.values()) {
			try {
				row.hours = Double.parseDouble(row.hoursTF.getText());
				total += row.hours;
				row.hoursTF.setForeground(normalColour);
			} catch (NumberFormatException e) {
				row.hoursTF.setForeground(errorColour);
				totalLabel.setText("ERROR");
				totalLabel.setForeground(errorColour);
				return;
			}
		}
		totalLabel.setText(decimalFormat.format(total));
		totalLabel.setForeground(normalColour);
		for (Row row : rows.values()) {
			String percentS = decimalFormat.format(total == 0 ? 0 : 100 * row.hours / total);
			row.percentL.setText("(" + percentS + "%)");
		}
		pack();
	}

	/**
	 * The function creates a button, which opens a file chooser and writes a
	 * summary of the displayed results into a user-selected file.
	 *
	 * @return The 'save to file' button.
	 */
	private JButton createSaveToFileButton() {
		JButton b = new JButton("SAVE TO FILE");
		b.setBackground(Color.BLACK);
		b.setForeground(Color.GRAY);
		b.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				try {
					checkTotal();
					fileChooser.setSelectedFile(new File(getDefaultFilename()));
					int returnValue = fileChooser.showDialog(ReviewDialog.this, "Save");
					if (returnValue != JFileChooser.APPROVE_OPTION) return;
					writeToFile(fileChooser.getSelectedFile());
					ReviewDialog.this.setVisible(false);
				} catch (Exception ex) {
					JOptionPane.showMessageDialog(ReviewDialog.this,
							ex.getMessage(), "Error occurred",
							JOptionPane.ERROR_MESSAGE);
				}
			}
		});
		return b;
	}

	/**
	 * Checks whether the interval has been set using the date chooser.
	 *
	 * @return True if custom.
	 */
	boolean isIntervalCustom() {
		return yearCB.getSelectedIndex() == 0 || weekCB.getSelectedIndex() == 0;
	}

	/**
	 * Returns the unique ID of a week prefixed with the year.
	 *
	 * @return Selected week's ID.
	 */
	String getSelectedWeekID() {
		String weekNo = weekCB.getSelectedItem().toString();
		if (weekNo.length() == 1)
			weekNo = "0" + weekNo;
		return yearCB.getSelectedItem() + "wk" + weekNo;
	}

	/**
	 * Create a default filename given the current date selection. If custom
	 * dates are selected, use those dates; otherwise, use year and week
	 * numbers.
	 *
	 * @return The default filename.
	 */
	private String getDefaultFilename() {
		if (yearCB.getSelectedIndex() == 0 || weekCB.getSelectedIndex() == 0)
			return "timesheet-"
			+ dateFormat.format(fromDate.getDate()).replaceAll("/", "")
			+ "-"
			+ dateFormat.format(toDate.getDate()).replaceAll("/", "")
			+ ".txt";
		return "timesheet-" + yearCB.getSelectedItem() + "wk"
		+ weekCB.getSelectedItem() + ".txt";
	}

	private String generateOverviewText() throws InsufficientDataException {
		StringBuilder sb = new StringBuilder();
		final String team = config.getTeam();
		double total = checkTotal();
		final String nl = System.getProperty("line.separator");
		for (Entry<String, Row> entry : rows.entrySet()) {
			double hours = Double.parseDouble(entry.getValue().hoursTF.getText());
			double fraction = hours / total;
			if (fraction < 0.004) continue;
			String line = team + ", " + decimalFormat.format(fraction) + ", " + entry.getKey();
			sb.append(line + nl);
		}
		return sb.toString();
	}

	/**
	 * This function writes the generated relative overview to a file.
	 *
	 * @param f
	 *            The file to write to.
	 * @throws IOException
	 *             Thrown if unable to open or write to the file.
	 * @throws InsufficientDataException
	 *             Thrown if unable to generate the overview.
	 */
	public void writeToFile(File f) throws IOException, InsufficientDataException {
		f.createNewFile();
		FileWriter fw = new FileWriter(f);
		fw.write(generateOverviewText());
		fw.close();
	}

	private JButton createCopyToClipboardButton() {
		JButton button = new JButton("COPY TO CLIPBOARD");
		button.setBackground(Color.BLACK);
		button.setForeground(Color.GRAY);
		button.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				try {
					copyOverviewToClipboard();
				} catch (InsufficientDataException ex) {
					ex.printStackTrace();
					JOptionPane.showMessageDialog(ReviewDialog.this,
							"Failed to copy the overview to clipboard: "
							+ ex.getMessage());
				}
			}
		});
		return button;
	}

	private void copyOverviewToClipboard() throws InsufficientDataException {
		String overview = generateOverviewText();
		Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
		clipboard.setContents(new StringSelection(overview), new ClipboardOwner() {
			@Override public void lostOwnership(Clipboard c, Transferable t) {}
		});
	}

	/**
	 * A helper function that obtains the total number of hours for the selected
	 * period.
	 *
	 * @return Total number of hours.
	 * @throws InsufficientDataException
	 *             Thrown if the total is zero.
	 */
	private double checkTotal() throws InsufficientDataException {
		double total = Double.parseDouble(totalLabel.getText());
		if (total == 0)
			throw new InsufficientDataException("Cannot submit data with no logged hours.");
		return total;
	}

	@Override
	public void caretUpdate(CaretEvent e) {
		recomputeTotal();
	}
}
