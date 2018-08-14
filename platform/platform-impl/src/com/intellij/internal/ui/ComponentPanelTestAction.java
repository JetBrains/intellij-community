// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.ui;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.ComponentWithBrowseButton;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.panel.ComponentPanel;
import com.intellij.openapi.ui.panel.ProgressPanel;
import com.intellij.ui.ComboboxWithBrowseButton;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.SideBorder;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTabbedPane;
import com.intellij.ui.components.labels.DropDownLink;
import com.intellij.ui.table.JBTable;
import com.intellij.util.Alarm;
import com.intellij.util.ObjectUtils;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UI;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.components.BorderLayoutPanel;
import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.Arrays;
import java.util.HashSet;

public class ComponentPanelTestAction extends DumbAwareAction {
  private enum Placement {
    Top    (SwingConstants.TOP, "Top"),
    Bottom (SwingConstants.BOTTOM, "Bottom"),
    Left   (SwingConstants.LEFT, "Left"),
    Right  (SwingConstants.RIGHT, "Right");

    private final String name;
    private final int placement;

    Placement(int placement, String name) {
      this.name = name;
      this.placement = placement;
    }

    @Override
    public String toString() {
      return name;
    }

    @MagicConstant(intValues = {SwingConstants.TOP, SwingConstants.BOTTOM, SwingConstants.LEFT, SwingConstants.RIGHT})
    public int placement() {
      return placement;
    }
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    if (project != null) {
      new ComponentPanelTest(project).show();
    }
  }

  @SuppressWarnings({"MethodMayBeStatic", "UseOfSystemOutOrSystemErr"})
  private static class ComponentPanelTest extends DialogWrapper {

    private static final HashSet<String> ALLOWED_VALUES = new HashSet<>(Arrays.asList("one", "two", "three", "four", "five", "six",
              "seven", "eight", "nine", "ten", "eleven", "twelve", "thirteen", "fourteen", "fifteen", "sixteen", "abracadabra"));

    private final Alarm myAlarm = new Alarm(getDisposable());
    private ProgressTimerRequest progressTimerRequest;

    private JTabbedPane pane;

    private ComponentPanelTest(Project project) {
      super(project);
      init();
      setTitle("Component Panel Test Action");
    }

    @Nullable
    @Override
    protected JComponent createCenterPanel() {
      pane = new JBTabbedPane(SwingConstants.TOP);
      pane.addTab("Component", createComponentPanel());
      pane.addTab("Component Grid", createComponentGridPanel());
      pane.addTab("Progress Grid", createProgressGridPanel());

      for (int i = 1; i <= 5; i++) {
        String title = "Blank " + i;
        JLabel label = new JLabel(title);
        pane.addTab(title, JBUI.Panels.simplePanel(label));
      }

      pane.addChangeListener(e -> {
        if (pane.getSelectedIndex() == 2) {
          myAlarm.addRequest(progressTimerRequest, 200, ModalityState.any());
        } else {
          myAlarm.cancelRequest(progressTimerRequest);
        }
      });

      BorderLayoutPanel panel = JBUI.Panels.simplePanel(pane);

      JPanel southPanel = new JPanel();
      southPanel.setLayout(new BoxLayout(southPanel, BoxLayout.X_AXIS));

      JCheckBox enabledCB = new JCheckBox("Enable TabPane", true);
      enabledCB.addActionListener(e -> pane.setEnabled(enabledCB.isSelected()));
      southPanel.add(enabledCB);

      southPanel.add(Box.createRigidArea(JBUI.size(UIUtil.DEFAULT_HGAP, 0)));

      JComboBox<Placement> placementCombo = new ComboBox<>(Placement.values());
      placementCombo.setSelectedIndex(0);
      placementCombo.addActionListener(e -> {
        Placement p = (Placement)placementCombo.getSelectedItem();
        if (p != null) pane.setTabPlacement(p.placement());
      });
      southPanel.add(placementCombo);
      southPanel.add(new Box.Filler(JBUI.size(0), JBUI.size(0), JBUI.size(Integer.MAX_VALUE, 0)));

      panel.addToBottom(southPanel);

      return panel;
    }

    private JComponent createComponentPanel() {
      JPanel topPanel = new JPanel(new GridBagLayout());
      GridBagConstraints gc = new GridBagConstraints(0, 0, 1, 1, 1.0, 0, GridBagConstraints.LINE_START, GridBagConstraints.HORIZONTAL, JBUI.insets(5, 0), 0, 0);

      JTextField text1 = new JTextField();
      Dimension d = text1.getPreferredSize();
      text1.setPreferredSize(new Dimension(JBUI.scale(100), d.height));

      topPanel.add(UI.PanelFactory.panel(text1).
        withLabel("&Textfield:").
        withComment("Textfield description").
        moveCommentRight().createPanel(), gc);

      JTextField text2 = new JTextField();
      gc.gridy++;
      topPanel.add(UI.PanelFactory.panel(text2).withLabel("&Path:").createPanel(), gc);

      ComponentPanel cp = ComponentPanel.getComponentPanel(text2);
      text1.getDocument().addDocumentListener(new DocumentAdapter() {
        @Override
        protected void textChanged(DocumentEvent e) {
          if (cp != null) {
            cp.setCommentText(text1.getText());
          }
        }
      });

      JCheckBox cb1 = new JCheckBox("Scroll tab layout");
      cb1.addActionListener(e -> pane.setTabLayoutPolicy(cb1.isSelected() ? JTabbedPane.SCROLL_TAB_LAYOUT : JTabbedPane.WRAP_TAB_LAYOUT));
      gc.gridy++;
      topPanel.add(UI.PanelFactory.panel(cb1).
        withComment("Set tabbed pane tabs layout property to SCROLL_TAB_LAYOUT").
        createPanel(), gc);

      JCheckBox cb2 = new JCheckBox("Full border");
      cb2.addActionListener(e -> pane.putClientProperty("JTabbedPane.hasFullBorder", Boolean.valueOf(cb2.isSelected())));
      gc.gridy++;
      topPanel.add(UI.PanelFactory.panel(cb2).
        withTooltip("Enable full border around the tabbed pane").createPanel(), gc);

      gc.gridy++;
      topPanel.add(UI.PanelFactory.panel(new JButton("Abracadabra")).
        withComment("Abradabra comment").resizeX(false).createPanel(), gc);

      String[] items = new String[]{ "One", "Two", "Three", "Four", "Five", "Six" };
      gc.gridy++;
      topPanel.add(UI.PanelFactory.panel(new JComboBox<>(items)).
        withComment("Combobox comment").createPanel(), gc);

      String[] columns = { "First column", "Second column" };
      String[][] data = {{"one", "1"}, {"two", "2"}, {"three", "3"}, {"four", "4"}, {"five", "5"},
        {"six", "6"}, {"seven", "7"}, {"eight", "8"}, {"nine", "9"}, {"ten", "10"}, {"eleven", "11"},
        {"twelve", "12"}, {"thirteen", "13"}, {"fourteen", "14"}, {"fifteen", "15"}, {"sixteen", "16"}};

      JBTable table = new JBTable(new DefaultTableModel() {
        @Override
        public String getColumnName(int column) { return columns[column]; }
        @Override
        public int getRowCount() { return data.length; }
        @Override
        public int getColumnCount() { return columns.length; }
        @Override
        public Object getValueAt(int row, int col) { return col == 0 ? data[row][col] : Integer.valueOf(data[row][col]); }
        @Override
        public boolean isCellEditable(int row, int column) { return true; }
        @Override
        public void setValueAt(Object value, int row, int col) {
          if (col == 0 && ALLOWED_VALUES.contains(value.toString()) || col == 1) {
            data[row][col] = value.toString();
            fireTableCellUpdated(row, col);
          }
        }
      });

      JTextField cellEditor = new JTextField();
      cellEditor.getDocument().addDocumentListener(new DocumentAdapter() {
        @Override
        protected void textChanged(DocumentEvent e) {
          Object op = ALLOWED_VALUES.contains(cellEditor.getText()) ? null : "error";
          cellEditor.putClientProperty("JComponent.outline", op);
        }
      });

      table.getColumnModel().getColumn(0).setCellEditor(new DefaultCellEditor(cellEditor));

      JComboBox<Integer> rightEditor = new ComboBox<>(Arrays.stream(data).map(i -> Integer.valueOf(i[1])).toArray(Integer[]::new));
      table.getColumnModel().getColumn(1).setCellEditor(new DefaultCellEditor(rightEditor));

      JBScrollPane pane = new JBScrollPane(table);
      pane.setPreferredSize(JBUI.size(400, 300));
      pane.putClientProperty(UIUtil.KEEP_BORDER_SIDES, SideBorder.ALL);

      BorderLayoutPanel mainPanel = JBUI.Panels.simplePanel(UI.PanelFactory.panel(pane).
        withLabel("Table label:").moveLabelOnTop().withComment("Table comment").resizeY(true).createPanel());
      mainPanel.addToTop(topPanel);

      return mainPanel;
    }

    private JComponent createComponentGridPanel() {
      ComponentWithBrowseButton cbb = new ComboboxWithBrowseButton(new JComboBox<>(new String[]{"One", "Two", "Three", "Four"}));
      cbb.addActionListener((e) -> System.out.println("Browse for combobox"));

      JBScrollPane pane = new JBScrollPane(new JTextArea(3, 40));
      pane.putClientProperty(UIUtil.KEEP_BORDER_SIDES, SideBorder.ALL);

      DropDownLink<String> linkLabel =
        new DropDownLink<>("Drop down link label",
                           Arrays.asList("Label 1",
                                         "Label 2 long long long long long long label",
                                         "Label 3", "Label 4", "Label 5", "Label 6"),
                           t -> System.out.println("[" + t + "] selected"), false);

      JPanel p1 = UI.PanelFactory.grid().
      add(UI.PanelFactory.panel(new JTextField()).
        withLabel("&Port:").withComment("Port comment")).

      add(UI.PanelFactory.panel(new JTextField()).
        withLabel("&Host:").withComment("Host comment")).

      add(UI.PanelFactory.panel(new JComboBox<>(new String[]{"HTTP", "HTTPS", "FTP", "SSL"})).
        withLabel("P&rotocol:").withComment("Protocol comment").withTooltip("Protocol selection").
        withTooltipLink("Check here for more info", ()-> System.out.println("More info"))).

      add(UI.PanelFactory.panel(new ComponentWithBrowseButton<>(new JTextField(), (e) -> System.out.println("Browse for text"))).
        withLabel("&Text field:").withComment("Text field comment")).

      add(UI.PanelFactory.panel(cbb).
        withLabel("&Combobox selection:")).

      add(UI.PanelFactory.panel(new JCheckBox("Checkbox")).withComment("Checkbox comment text")).

      add(UI.PanelFactory.panel(pane).
        withLabel("Text area:").
        anchorLabelOn(UI.Anchor.Top).
        withComment("Text area comment").
        moveLabelOnTop().
        withTopRightComponent(linkLabel)
      ).createPanel();

      ButtonGroup bg = new ButtonGroup();
      JRadioButton rb1 = new JRadioButton("RadioButton 1");
      JRadioButton rb2 = new JRadioButton("RadioButton 2");
      JRadioButton rb3 = new JRadioButton("RadioButton 3");
      bg.add(rb1);
      bg.add(rb2);
      bg.add(rb3);
      rb1.setSelected(true);

      JPanel p2 = UI.PanelFactory.grid().
        add(UI.PanelFactory.panel(new JCheckBox("Checkbox 1")).withComment("Comment 1").moveCommentRight()).
        add(UI.PanelFactory.panel(new JCheckBox("Checkbox 2")).withComment("Comment 2")).
        add(UI.PanelFactory.panel(new JCheckBox("Checkbox 3")).withTooltip("Checkbox tooltip")).

        add(UI.PanelFactory.panel(rb1).withComment("Comment 1").moveCommentRight()).
        add(UI.PanelFactory.panel(rb2).withComment("Comment 2")).
        add(UI.PanelFactory.panel(rb3).withTooltip("RadioButton tooltip")).

        createPanel();

      JPanel panel = new JPanel();
      panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
      panel.setBorder(JBUI.Borders.emptyTop(5));
      panel.add(p1);
      panel.add(Box.createVerticalStrut(JBUI.scale(5)));
      panel.add(p2);
      panel.add(new Box.Filler(JBUI.size(100,20), JBUI.size(200,30), JBUI.size(Integer.MAX_VALUE, Integer.MAX_VALUE)));

      return panel;
    }

    private class ProgressTimerRequest implements Runnable {
      private final JProgressBar myProgressBar;

      private ProgressTimerRequest(JProgressBar progressBar) {
        myProgressBar = progressBar;
      }

      @Override public void run() {
        if (canPlay()) {
          int v = myProgressBar.getValue() + 1;
          if (v > myProgressBar.getMaximum()) {
            v = myProgressBar.getMinimum();
          }
          myProgressBar.setValue(v);

          ProgressPanel progressPanel = ProgressPanel.getProgressPanel(myProgressBar);
          if (progressPanel != null) {
            progressPanel.setCommentText(Integer.toString(v));
          }
          myAlarm.addRequest(this, 200, ModalityState.any());
        }
      }

      private boolean canPlay() {
        ProgressPanel progressPanel = ProgressPanel.getProgressPanel(myProgressBar);
        return progressPanel != null && progressPanel.getState() == ProgressPanel.State.PLAYING;
      }
    }

    private JComponent createProgressGridPanel() {
      JPanel panel = new JPanel();
      panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));

      JProgressBar pb1 = new JProgressBar(0, 100);
      JProgressBar pb2 = new JProgressBar(0, 100);

      progressTimerRequest = new ProgressTimerRequest(pb1);

      ProgressPanel progressPanel = ProgressPanel.getProgressPanel(pb1);
      if (progressPanel != null) {
        progressPanel.setCommentText(Integer.toString(0));
      }

      panel.add(UI.PanelFactory.grid().
        add(UI.PanelFactory.panel(pb1).
          withLabel("Label 1.1").
          withCancel(()-> myAlarm.cancelRequest(progressTimerRequest)).
          andCancelText("Stop")).
        add(UI.PanelFactory.panel(pb2).
          withLabel("Label 1.2").
          withPause(()-> System.out.println("Pause action #2")).
          withResume(()-> System.out.println("Resume action #2"))).
                                 resize().
        createPanel());

      ObjectUtils.assertNotNull(ProgressPanel.getProgressPanel(pb1)).setCommentText("Long long long long long long long text");
      ObjectUtils.assertNotNull(ProgressPanel.getProgressPanel(pb2)).setCommentText("Short text");

      JProgressBar pb3 = new JProgressBar(0, 100);
      JProgressBar pb4 = new JProgressBar(0, 100);
      panel.add(UI.PanelFactory.grid().
        add(UI.PanelFactory.panel(pb3).
          withLabel("Label 2.1").moveLabelLeft().
          withCancel(()-> System.out.println("Cancel action #3"))).
        add(UI.PanelFactory.panel(pb4).
          withTopSeparator().
          withLabel("Label 2.2").moveLabelLeft().
          withPause(()-> System.out.println("Pause action #4")).
          withResume(()-> System.out.println("Resume action #4"))).
                                 resize().
        createPanel());

      ObjectUtils.assertNotNull(ProgressPanel.getProgressPanel(pb3)).setCommentText("Long long long long long long text");
      ObjectUtils.assertNotNull(ProgressPanel.getProgressPanel(pb4)).setCommentText("Short text");

      panel.add(UI.PanelFactory.grid().
        add(UI.PanelFactory.panel(new JProgressBar(0, 100)).
          withTopSeparator().withoutComment().
          andCancelAsButton().
          withCancel(()-> System.out.println("Cancel action #11"))).
        createPanel());

      return JBUI.Panels.simplePanel().addToTop(panel);
    }
  }
}
