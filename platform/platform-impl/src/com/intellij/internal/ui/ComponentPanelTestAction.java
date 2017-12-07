// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.ui;

import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComponentWithBrowseButton;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.panel.JBPanelFactory;
import com.intellij.openapi.ui.panel.ProgressPanel;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.ui.ComboboxWithBrowseButton;
import com.intellij.ui.tabs.TabInfo;
import com.intellij.ui.tabs.TabsListener;
import com.intellij.ui.tabs.impl.JBEditorTabs;
import com.intellij.util.Alarm;
import com.intellij.util.ObjectUtils;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
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
    private ProgressTimerRequest timerRequest;

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

      TabInfo progressTab = new TabInfo(createProgressPanel()).setText("Progress");
      tabs.addTab(progressTab);

      tabs.addListener(new TabsListener.Adapter(){
        @Override
        public void selectionChanged(TabInfo oldSelection, TabInfo newSelection) {
          if (timerRequest != null && timerRequest.canPlay()) {
            if (newSelection == progressTab) {
              myAlarm.addRequest(timerRequest, 200, ModalityState.any());
            } else {
              myAlarm.cancelRequest(timerRequest);
            }
          }
        }
      });

      tabs.addTab(new TabInfo(createProgressGridPanel()).setText("Progress Grid"));

      return tabs;
    }

    private JComponent createComponentPanel() {
      JPanel panel = new JPanel();
      panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
      panel.setBorder(JBUI.Borders.emptyTop(5));

      JTextField text = new JTextField();
      Dimension d = text.getPreferredSize();
      text.setPreferredSize(new Dimension(JBUI.scale(100), d.height));

      panel.add(JBPanelFactory.panel(text).
        withLabel("&Textfield:").
        withComment("My short description").
        moveCommentRight().createPanel());

      panel.add(JBPanelFactory.panel(new JTextField()).
        withLabel("&Path:").createPanel());

      panel.add(JBPanelFactory.panel(new JTextField()).
        withLabel("&Options:").createPanel());

      panel.add(JBPanelFactory.panel(new JCheckBox("This is a checkbox 1")).
        withComment("My long long long long long long long long long long comment").
        createPanel());

      panel.add(JBPanelFactory.panel(new JCheckBox("This is a checkbox 2")).
        withTooltip("Help tooltip description").createPanel());

      panel.add(JBPanelFactory.panel(new JButton("Abracadabra")).
        withComment("Do abradabra stuff").createPanel());

      String[] items = new String[]{ "One", "Two", "Three", "Four", "Five", "Six" };
      panel.add(JBPanelFactory.panel(new JComboBox<>(items)).
        withComment("Very important combobox").createPanel());

      panel.add(JBPanelFactory.panel(new JCheckBox("Checkbox")).
        withTooltip("Checkbox description").withComment("Checkbox comment").createPanel());

      panel.add(JBPanelFactory.panel(new JRadioButton("Radiobutton")).
        withTooltip("Radiobutton description").withComment("Radiobutton comment").createPanel());

      panel.add(JBPanelFactory.panel(new JTextArea(3, 40)).
        withLabel("Extra arguments:").moveLabelOnTop().createPanel());

      return panel;
    }

    private JComponent createComponentGridPanel() {
      ComponentWithBrowseButton cbb = new ComboboxWithBrowseButton(new JComboBox<>(new String[]{"One", "Two", "Three", "Four"}));
      cbb.addActionListener((e) -> System.out.println("Browse for combobox"));

      JPanel panel = JBPanelFactory.grid().
        add(JBPanelFactory.panel(new JTextField()).
          withLabel("&Port:").withComment("Port")).

        add(JBPanelFactory.panel(new JTextField()).
          withLabel("&Host:").withComment("Host")).

        add(JBPanelFactory.panel(new JComboBox<>(new String[]{"HTTP", "HTTPS", "FTP", "SSL"})).
          withLabel("P&rotocol:").withComment("Choose protocol").withTooltip("Protocol selection").
          withTooltipLink("Check here for more info", ()-> System.out.println("More info"))).

        add(JBPanelFactory.panel(new ComponentWithBrowseButton<>(new JTextField(), (e) -> System.out.println("Browse for text"))).
          withLabel("&Set parameters:").withComment("Runtime parameters")).

        add(JBPanelFactory.panel(cbb).
          withLabel("&Detect runlevel:")).

        add(JBPanelFactory.panel(new JCheckBox("Run in a loop")).
              withComment("Comment text")).

        add(JBPanelFactory.panel(new JTextArea(3, 40)).
          withLabel("Extra arguments:").moveLabelOnTop()).

        createPanel();

      panel.setBorder(JBUI.Borders.emptyTop(5));
      return panel;
    }

    private class ProgressTimerRequest implements Runnable {
      private final JProgressBar myProgressBar;

      private ProgressTimerRequest(JProgressBar progressBar) {
        myProgressBar = progressBar;
      }

      @Override public void run() {
        int v = myProgressBar.getValue() + 1;
        if (v > myProgressBar.getMaximum()) {
          v = myProgressBar.getMinimum();
        }
        myProgressBar.setValue(v);

        ProgressPanel progressPanel = ProgressPanel.forComponent(myProgressBar);
        if (progressPanel != null) {
          progressPanel.setCommentText(Integer.toString(v));
        }
        myAlarm.addRequest(this, 200, ModalityState.any());
      }

      private boolean canPlay() {
        ProgressPanel progressPanel = ProgressPanel.forComponent(myProgressBar);
        return progressPanel != null && progressPanel.getState() == ProgressPanel.State.PLAYING;
      }
    }

    private JComponent createProgressPanel() {
      JProgressBar progressBar = new JProgressBar(0, 100);

      JPanel innerPanel = JBPanelFactory.panel(progressBar).
        withLabel("Process").withCancel(() -> myAlarm.cancelRequest(timerRequest)).
        createPanel();

      timerRequest = new ProgressTimerRequest(progressBar);

      myAlarm.addRequest(timerRequest, 200, ModalityState.any());

      ProgressPanel progressPanel = ProgressPanel.forComponent(progressBar);
      if (progressPanel != null) {
        progressPanel.setCommentText(Integer.toString(0));
      }

      JPanel panel = new JPanel(new BorderLayout());
      panel.setBorder(JBUI.Borders.emptyTop(5));
      panel.add(innerPanel, BorderLayout.NORTH);
      return panel;
    }

    private JComponent createProgressGridPanel() {
      JPanel panel = new JPanel();
      panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));

      JProgressBar pb1 = new JProgressBar(0, 100);
      JProgressBar pb2 = new JProgressBar(0, 100);

      panel.add(JBPanelFactory.grid().
        add(JBPanelFactory.panel(pb1).
          withLabel("Label 1.1").
          withCancel(()-> System.out.println("Cancel action #1"))).
        add(JBPanelFactory.panel(pb2).
          withLabel("Label 1.2").
          withPause(()-> System.out.println("Pause action #2")).
          withResume(()-> System.out.println("Resume action #2"))).
        createPanel());

      ObjectUtils.assertNotNull(ProgressPanel.forComponent(pb1)).setCommentText("Long long long long long long long text");
      ObjectUtils.assertNotNull(ProgressPanel.forComponent(pb2)).setCommentText("Short text");

      JProgressBar pb3 = new JProgressBar(0, 100);
      JProgressBar pb4 = new JProgressBar(0, 100);
      panel.add(JBPanelFactory.grid().
        add(JBPanelFactory.panel(pb3).
          withTopSeparator().
          withLabel("Label 2.1").moveLabelLeft().
          withCancel(()-> System.out.println("Cancel action #3"))).
        add(JBPanelFactory.panel(pb4).
          withLabel("Label 2.2").moveLabelLeft().
          withPause(()-> System.out.println("Pause action #4")).
          withResume(()-> System.out.println("Resume action #4"))).
        createPanel());

      ObjectUtils.assertNotNull(ProgressPanel.forComponent(pb3)).setCommentText("Long long long long long long text");
      ObjectUtils.assertNotNull(ProgressPanel.forComponent(pb4)).setCommentText("Short text");

      JProgressBar pb5 = new JProgressBar(0, 100);
      JProgressBar pb6 = new JProgressBar(0, 100);
      panel.add(JBPanelFactory.grid().
        add(JBPanelFactory.panel(pb5).
          withTopSeparator().withLabel("Label 3.1").
          withCancel(()-> System.out.println("Cancel action #5")).
          andSmallIcons()).
        add(JBPanelFactory.panel(pb6).
          withLabel("Label 3.2").
          withPause(()-> System.out.println("Pause action #6")).
          withResume(()-> System.out.println("Resume action #6")).
          andSmallIcons()).createPanel());

      ObjectUtils.assertNotNull(ProgressPanel.forComponent(pb5)).setCommentText("Long long long long long long text");
      ObjectUtils.assertNotNull(ProgressPanel.forComponent(pb6)).setCommentText("Short text");

      panel.add(JBPanelFactory.grid().
        add(JBPanelFactory.panel(new JProgressBar(0, 100)).
          withTopSeparator().withLabel("Label 4.1").withoutComment().
          withCancel(()-> System.out.println("Cancel action #7")).
          andSmallIcons()).
        add(JBPanelFactory.panel(new JProgressBar(0, 100)).
          withLabel("Label 4.2").withoutComment().
          withPause(()-> System.out.println("Pause action #8")).
          withResume(()-> System.out.println("Resume action #8")).
          andSmallIcons()).
        createPanel());


      panel.add(JBPanelFactory.grid().
        add(JBPanelFactory.panel(new JProgressBar(0, 100)).
          withTopSeparator().withoutComment().
          withCancel(()-> System.out.println("Cancel action #9")).
          andSmallIcons()).
        createPanel());

      panel.add(JBPanelFactory.grid().
        add(JBPanelFactory.panel(new JProgressBar(0, 100)).
          withTopSeparator().withoutComment().
          andCancelAsButton().
          withCancel(()-> System.out.println("Cancel action #11"))).
        createPanel());

      panel.add(Box.createVerticalBox());
      return panel;
    }
  }
}
