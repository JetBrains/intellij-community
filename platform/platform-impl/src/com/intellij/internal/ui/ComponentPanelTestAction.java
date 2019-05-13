// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.ui;

import com.intellij.ide.ui.laf.darcula.DarculaUIUtil;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.fileTypes.FileTypes;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.*;
import com.intellij.openapi.ui.panel.ComponentPanel;
import com.intellij.openapi.ui.panel.ProgressPanel;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.*;
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
import javax.swing.event.HyperlinkEvent;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableColumn;
import java.awt.*;
import java.net.MalformedURLException;
import java.net.URL;
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

    private static final String[] STRING_VALUES = { "One", "Two", "Three", "Four", "Five", "Six" };

    private final Alarm myAlarm = new Alarm(getDisposable());
    private ProgressTimerRequest progressTimerRequest;

    private JTabbedPane   pane;
    private final Project project;

    private ComponentPanelTest(Project project) {
      super(project);

      this.project = project;

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
      pane.addTab("Validators", createValidatorsPanel());
      pane.addTab("Multilines", createMultilinePanel());

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
      new ComponentValidator(getDisposable()).
        withHyperlinkListener(e -> {
          if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
            System.out.println("Text1 link clicked. Desc = " + e.getDescription());
          }
        }).withValidator(() -> {
          String tt = text1.getText();
          if (StringUtil.isNotEmpty(tt)) {
            try {
              Integer.parseInt(tt);
              return null;
            }
            catch (NumberFormatException nex) {
              return new ValidationInfo("Warning, expecting a number.<br/>Visit the <a href=\"#link.one\">information link</a>" +
                                              "<br/>Or <a href=\"#link.two\">another link</a>", text1).asWarning();
            }
          }
          else {
            return null;
          }
        }).withFocusValidator(() -> {
          String tt = text1.getText();
          if (StringUtil.isNotEmpty(tt)) {
            try {
              int i = Integer.parseInt(tt);
              return i == 555 ? new ValidationInfo("Wrong number", text1).asWarning() : null;
            }
            catch (NumberFormatException nex) {
              return new ValidationInfo("Warning, expecting a number.", text1).asWarning();
            }
          } else {
            return null;
          }
        }).installOn(text1);

      Dimension d = text1.getPreferredSize();
      text1.setPreferredSize(new Dimension(JBUI.scale(100), d.height));

      topPanel.add(UI.PanelFactory.panel(text1).
        withLabel("&Textfield:").
        withComment("Textfield description").
        moveCommentRight().createPanel(), gc);

      JTextField text2 = new JTextField();
      new ComponentValidator(getDisposable()).
        withHyperlinkListener(e -> {
          if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
            System.out.println("Text2 link clicked. Desc = " + e.getDescription());
          }
        }).withValidator(() -> {
          String tt = text2.getText();
          return StringUtil.isEmpty(tt) || tt.length() < 5 ?
            new ValidationInfo("Message is too short.<br/>Should contain at least 5 symbols.<br/>Please <a href=\"#check.rules\">check rules.</a>", text2) : null;
        }).andStartOnFocusLost().installOn(text2);

      gc.gridy++;
      topPanel.add(UI.PanelFactory.panel(text2).withLabel("&Path:").createPanel(), gc);

      ComponentPanel cp = ComponentPanel.getComponentPanel(text2);
      text1.getDocument().addDocumentListener(new DocumentAdapter() {
        @Override
        protected void textChanged(@NotNull DocumentEvent e) {
          String text = text1.getText();
          if (cp != null) {
            cp.setCommentText(text);
          }

          ComponentValidator.getInstance(text1).ifPresent(v -> v.revalidate());
        }
      });

      text2.getDocument().addDocumentListener(new DocumentAdapter() {
        @Override
        protected void textChanged(@NotNull DocumentEvent e) {
          ComponentValidator.getInstance(text2).ifPresent(v -> v.revalidate());
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

      gc.gridy++;
      topPanel.add(UI.PanelFactory.panel(new JComboBox<>(STRING_VALUES)).
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
      cellEditor.putClientProperty(DarculaUIUtil.COMPACT_PROPERTY, Boolean.TRUE);
      cellEditor.getDocument().addDocumentListener(new DocumentAdapter() {
        @Override
        protected void textChanged(@NotNull DocumentEvent e) {
          Object op = ALLOWED_VALUES.contains(cellEditor.getText()) ? null : "error";
          cellEditor.putClientProperty("JComponent.outline", op);
        }
      });


      TableColumn col0 = table.getColumnModel().getColumn(0);
      col0.setCellEditor(new DefaultCellEditor(cellEditor));
      col0.setCellRenderer(new DefaultTableCellRenderer() {
        @Override
        public Dimension getPreferredSize() {
          Dimension size = super.getPreferredSize();
          Dimension editorSize = cellEditor.getPreferredSize();
          size.height = Math.max(size.height, editorSize.height);
          return size;
        }
      });

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
        add(UI.PanelFactory.panel(new JCheckBox("<html>Multiline<br/>Checkbox 3</html>")).withTooltip("Checkbox tooltip")).

        add(UI.PanelFactory.panel(rb1).withComment("Comment 1").moveCommentRight()).
        add(UI.PanelFactory.panel(rb2).withComment("Comment 2").withTooltip("Checkbox tooltip")).
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

    private JComponent createValidatorsPanel() {
      // JTextField component with browse button
      TextFieldWithBrowseButton tfbb = new TextFieldWithBrowseButton(e -> System.out.println("JTextField browse button pressed"));
      new ComponentValidator(getDisposable()).withValidator(() -> tfbb.getText().length() != 5 ? new ValidationInfo("Enter 5 symbols",  tfbb) : null).
        withOutlineProvider(ComponentValidator.CWBB_PROVIDER).
        andStartOnFocusLost().
        installOn(tfbb);

      tfbb.getTextField().getDocument().addDocumentListener(new DocumentAdapter() {
        @Override
        protected void textChanged(@NotNull DocumentEvent e) {
          ComponentValidator.getInstance(tfbb).ifPresent(ComponentValidator::revalidate);
        }
      });

      // EditorTextField component with browse button
      EditorTextField editor = new EditorTextField();
      ComponentWithBrowseButton<EditorTextField> etfbb = new ComponentWithBrowseButton<>(editor, e -> System.out.println("JTextField browse button pressed"));
      new ComponentValidator(getDisposable()).withValidator(() -> {
        try {
          new URL(etfbb.getChildComponent().getDocument().getText());
          return null;
        } catch (MalformedURLException mex) {
          return new ValidationInfo("Enter a valid URL", etfbb);
        }
      }).withOutlineProvider(ComponentValidator.CWBB_PROVIDER).andStartOnFocusLost().installOn(etfbb);

      etfbb.getChildComponent().getDocument().addDocumentListener(new DocumentListener() {
        @Override
        public void documentChanged(@NotNull com.intellij.openapi.editor.event.DocumentEvent event) {
          ComponentValidator.getInstance(etfbb).ifPresent(ComponentValidator::revalidate);
        }
      });

      // EditorComboBoxEditor
      ComboBox<String> comboBox = new ComboBox<>(STRING_VALUES);
      EditorComboBoxEditor cbEditor = new EditorComboBoxEditor(project, FileTypes.PLAIN_TEXT);
      comboBox.setEditor(cbEditor);
      comboBox.addActionListener(l -> ComponentValidator.getInstance(comboBox).ifPresent(ComponentValidator::revalidate));

      new ComponentValidator(getDisposable())
        .withValidator(() -> comboBox.getSelectedIndex() % 2 == 0 ? new ValidationInfo("Can't select odd items", comboBox) : null)
        .installOn(comboBox);

      // Panels factory
      return UI.PanelFactory.grid().
        add(UI.PanelFactory.panel(tfbb).
          withLabel("&TextField:").withComment("Text field with browse button")).

        add(UI.PanelFactory.panel(etfbb).
          withLabel("&EditorTextField:").withComment("EditorTextField with browse button")).

        add(UI.PanelFactory.panel(comboBox).
          withLabel("&ComboBoxEditorTextField:").withComment("EditorComboBox editor")).

        createPanel();
    }

    private JComponent createMultilinePanel() {
      JPanel panel = new JPanel(new GridBagLayout());
      GridBagConstraints gc = new GridBagConstraints(0, 0, 1, 1, 0.0, 0.0, GridBagConstraints.LINE_START,
                                                     GridBagConstraints.HORIZONTAL, JBUI.insets(10, 0, 0, 4), 0, 0);

      panel.add(new JLabel("Label one:"), gc);

      gc.gridx++;
      panel.add(new JCheckBox("<html>Multiline<br/>html<br/>checkbox</html>"), gc);

      gc.gridx++;
      panel.add(new JCheckBox("<html>Single line html checkbox</html>"), gc);

      gc.gridx++;
      panel.add(new JCheckBox("Single line checkbox"), gc);

      gc.gridx++;
      panel.add(new JButton("Button 1"), gc);

      gc.gridy++;
      gc.gridx = 0;
      panel.add(new JLabel("Label two:"), gc);

      ButtonGroup bg = new ButtonGroup();
      JRadioButton rb = new JRadioButton("<html>Multiline<br/>html<br/>radiobutton</html>");
      bg.add(rb);
      rb.setSelected(true);

      gc.gridx++;
      panel.add(rb, gc);

      rb = new JRadioButton("<html>Single line html radiobutton</html>");
      bg.add(rb);

      gc.gridx++;
      panel.add(rb, gc);

      rb = new JRadioButton("Single line radiobutton");
      bg.add(rb);

      gc.gridx++;
      panel.add(rb, gc);

      gc.gridx++;
      panel.add(new JButton("Button 2"), gc);

      gc.gridy++;
      gc.gridx = 0;
      gc.anchor = GridBagConstraints.PAGE_END;
      gc.fill = GridBagConstraints.BOTH;
      gc.weightx = 1.0;
      gc.weighty = 1.0;
      gc.gridwidth = 5;
      panel.add(new JPanel(), gc);

      return JBUI.Panels.simplePanel().addToTop(panel);
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
