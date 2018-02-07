// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.ui;

import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComponentWithBrowseButton;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.panel.ComponentPanel;
import com.intellij.openapi.ui.panel.ProgressPanel;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.ui.ComboboxWithBrowseButton;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.SideBorder;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.table.JBTable;
import com.intellij.ui.tabs.TabInfo;
import com.intellij.ui.tabs.TabsListener;
import com.intellij.ui.tabs.impl.JBEditorTabs;
import com.intellij.util.Alarm;
import com.intellij.util.ObjectUtils;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.table.AbstractTableModel;
import java.awt.*;

public class ComponentPanelTestAction extends DumbAwareAction {
  @Override
  public void actionPerformed(AnActionEvent e) {
    Project project = e.getProject();
    if (project != null) {
      new ComponentPanelTest(project).show();
    }
  }

  @SuppressWarnings({"MethodMayBeStatic", "UseOfSystemOutOrSystemErr"})
  private static class ComponentPanelTest extends DialogWrapper {
    private final Project myProject;
    private final Alarm myAlarm = new Alarm(getDisposable());
    private ProgressTimerRequest progressTimerRequest;

    private ComponentPanelTest(Project project) {
      super(project);
      myProject = project;
      init();
    }

    @Nullable
    @Override
    protected JComponent createCenterPanel() {
      JBEditorTabs tabs = new JBEditorTabs(myProject, ActionManager.getInstance(), IdeFocusManager.getInstance(myProject), getDisposable()) {
        @Override
        public boolean isAlphabeticalMode() {
          return false;
        }
      };

      tabs.addTab(new TabInfo(createComponentPanel()).setText("Component"));
      tabs.addTab(new TabInfo(createComponentGridPanel()).setText("Component Grid"));

      TabInfo progressTab = new TabInfo(createProgressGridPanel()).setText("Progress Grid");
      tabs.addTab(progressTab);

      tabs.addListener(new TabsListener.Adapter(){
        @Override
        public void selectionChanged(TabInfo oldSelection, TabInfo newSelection) {
          if (newSelection == progressTab) {
            myAlarm.addRequest(progressTimerRequest, 200, ModalityState.any());
          } else {
            myAlarm.cancelRequest(progressTimerRequest);
          }
        }
      });

      return tabs;
    }

    private JComponent createComponentPanel() {
      JPanel panel = new JPanel();
      panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
      panel.setBorder(JBUI.Borders.emptyTop(5));

      JTextField text1 = new JTextField();
      Dimension d = text1.getPreferredSize();
      text1.setPreferredSize(new Dimension(JBUI.scale(100), d.height));

      panel.add(UI.PanelFactory.panel(text1).
        withLabel("&Textfield:").
        withComment("Textfield description").
        moveCommentRight().createPanel());

      JTextField text2 = new JTextField();
      panel.add(UI.PanelFactory.panel(text2).
        withLabel("&Path:").createPanel());

      ComponentPanel cp = ComponentPanel.getComponentPanel(text2);
      text1.getDocument().addDocumentListener(new DocumentAdapter() {
        @Override
        protected void textChanged(DocumentEvent e) {
          if (cp != null) {
            cp.setCommentText(text1.getText());
          }
        }
      });

      panel.add(UI.PanelFactory.panel(new JCheckBox("This is a checkbox 1")).
        withComment("My long long long long long long long long long long comment").
        createPanel());

      panel.add(UI.PanelFactory.panel(new JCheckBox("This is a checkbox 2")).
        withTooltip("Help tooltip description").createPanel());

      panel.add(UI.PanelFactory.panel(new JButton("Abracadabra")).
        withComment("Abradabra comment").createPanel());

      String[] items = new String[]{ "One", "Two", "Three", "Four", "Five", "Six" };
      panel.add(UI.PanelFactory.panel(new JComboBox<>(items)).
        withComment("Combobox comment").createPanel());

      String[] columns = { "First column", "Second column" };
      String[][] data = {{"one", "1"}, {"two", "2"}, {"three", "3"}, {"four", "4"}, {"five", "5"},
        {"six", "6"}, {"seven", "7"}, {"eight", "8"}, {"nine", "9"}, {"ten", "10"}, {"eleven", "11"},
        {"twelve", "12"}, {"thirteen", "13"}, {"fourteen", "14"}, {"fifteen", "15"}, {"sixteen", "16"}};

      JBTable table = new JBTable(new AbstractTableModel() {
        public String getColumnName(int column) { return columns[column]; }
        public int getRowCount() { return data.length; }
        public int getColumnCount() { return columns.length; }
        public Object getValueAt(int row, int col) { return data[row][col]; }
        public boolean isCellEditable(int row, int column) { return false; }
        public void setValueAt(Object value, int row, int col) {}
      });

      JBScrollPane pane = new JBScrollPane(table);
      pane.setPreferredSize(JBUI.size(200, 100));
      pane.putClientProperty(UIUtil.KEEP_BORDER_SIDES, SideBorder.ALL);

      panel.add(UI.PanelFactory.panel(pane).
        withLabel("Table label:").moveLabelOnTop().withComment("Table comment").createPanel());

      panel.add(new Box.Filler(JBUI.size(100,20), JBUI.size(200,30), JBUI.size(Integer.MAX_VALUE, Integer.MAX_VALUE)));
      return panel;
    }

    private JComponent createComponentGridPanel() {
      ComponentWithBrowseButton cbb = new ComboboxWithBrowseButton(new JComboBox<>(new String[]{"One", "Two", "Three", "Four"}));
      cbb.addActionListener((e) -> System.out.println("Browse for combobox"));

      JBScrollPane pane = new JBScrollPane(new JTextArea(3, 40));
      pane.putClientProperty(UIUtil.KEEP_BORDER_SIDES, SideBorder.ALL);

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
        withLabel("Text area:").withComment("Text area comment").moveLabelOnTop()).

      createPanel();

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

      myAlarm.addRequest(progressTimerRequest, 200, ModalityState.any());

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
        expandVertically().
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
        expandVertically().
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
