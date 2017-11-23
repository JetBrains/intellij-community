// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.ui;

import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComponentWithBrowseButton;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.panel.*;
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

      panel.add(JBPanelFactory.getInstance().createComponentPanelBuilder(text).
        setLabelText("&Textfield:").
        setCommentText("My short description").
        setCommentLocation(SwingConstants.RIGHT).createPanel());

      panel.add(JBPanelFactory.getInstance().createComponentPanelBuilder(new JTextField()).
        setLabelText("&Path:").createPanel());

      panel.add(JBPanelFactory.getInstance().createComponentPanelBuilder(new JTextField()).
        setLabelText("&Options:").createPanel());

      panel.add(JBPanelFactory.getInstance().createComponentPanelBuilder(new JCheckBox("This is a checkbox 1")).
        setCommentText("My long long long long long long long long long long comment").
        setCommentLocation(SwingConstants.BOTTOM).createPanel());

      panel.add(JBPanelFactory.getInstance().createComponentPanelBuilder(new JCheckBox("This is a checkbox 2")).
        setHelpTooltipText("Help tooltip description").createPanel());

      panel.add(JBPanelFactory.getInstance().createComponentPanelBuilder(new JButton("Abracadabra")).
        setCommentText("Do abradabra stuff").setCommentLocation(SwingConstants.BOTTOM).createPanel());

      String[] items = new String[]{ "One", "Two", "Three", "Four", "Five", "Six" };
      panel.add(JBPanelFactory.getInstance().createComponentPanelBuilder(new JComboBox<>(items)).
        setCommentText("Very important combobox").setCommentLocation(SwingConstants.BOTTOM).createPanel());

      panel.add(JBPanelFactory.getInstance().createComponentPanelBuilder(new JCheckBox("Checkbox")).
        setHelpTooltipText("Checkbox description").
        setCommentText("Checkbox comment").setCommentLocation(SwingConstants.BOTTOM).createPanel());

      panel.add(JBPanelFactory.getInstance().createComponentPanelBuilder(new JRadioButton("Radiobutton")).
        setHelpTooltipText("Radiobutton description").
        setCommentText("Radiobutton comment").setCommentLocation(SwingConstants.BOTTOM).createPanel());

      return panel;
    }

    private JComponent createComponentGridPanel() {
      ComponentWithBrowseButton cbb = new ComboboxWithBrowseButton(new JComboBox<>(new String[]{"One", "Two", "Three", "Four"}));
      cbb.addActionListener((e) -> System.out.println("Browse for combobox"));

      JBPanelFactory pf = JBPanelFactory.getInstance();
      PanelGridBuilder<ComponentPanelBuilder> gb = pf.createComponentPanelGridBuilder().
        add(pf.createComponentPanelBuilder(new JTextField()).
          setLabelText("&Port:").
          setCommentText("Port").
          setCommentLocation(SwingConstants.BOTTOM)).
        add(pf.createComponentPanelBuilder(new JTextField()).
          setLabelText("&Host:").
          setCommentText("Host").
          setCommentLocation(SwingConstants.BOTTOM)).
        add(pf.createComponentPanelBuilder(new JComboBox<>(new String[]{"HTTP", "HTTPS", "FTP", "SSL"})).
          setLabelText("P&rotocol:").
          setCommentText("Choose protocol").
          setCommentLocation(SwingConstants.BOTTOM).
          setHelpTooltipText("Protocol selection").
          setHelpTooltipLink("Check here for more info", ()-> System.out.println("More info"))).
        add(pf.createComponentPanelBuilder(new ComponentWithBrowseButton<>(new JTextField(), (e) -> System.out.println("Browse for text"))).
          setLabelText("&Set parameters:").
          setCommentText("Runtime parameters").
          setCommentLocation(SwingConstants.BOTTOM)).
        add(pf.createComponentPanelBuilder(cbb).
          setLabelText("&Detect runlevel:")).
        add(pf.createComponentPanelBuilder(new JCheckBox("Run in a loop")).
              setCommentText("Comment text").setCommentLocation(SwingConstants.RIGHT)
            //setHelpTooltipText("Allow to run endlessly").
            //setHelpTooltipLink("Check here for more info", ()-> System.out.println("More info"))
        );

      JPanel panel = gb.createPanel();
      if (panel != null) {
        panel.setBorder(JBUI.Borders.emptyTop(5));
      }
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
      JBPanelFactory pf = JBPanelFactory.getInstance();
      JProgressBar progressBar = new JProgressBar(0, 100);

      ProgressPanelBuilder pb = pf.createProgressPanelBuilder(progressBar).
        setLabelText("Process").
        setLabelLocation(SwingConstants.TOP).
        setCancelAction(() -> myAlarm.cancelRequest(timerRequest));

      JPanel innerPanel = pb.createPanel();
      timerRequest = new ProgressTimerRequest(progressBar);

      myAlarm.addRequest(timerRequest, 200, ModalityState.any());

      ProgressPanel progressPanel = ProgressPanel.forComponent(progressBar);
      if (progressPanel != null) {
        progressPanel.setCommentText(Integer.toString(0));
      }

      if (innerPanel != null) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(JBUI.Borders.emptyTop(5));
        panel.add(innerPanel, BorderLayout.NORTH);
        return panel;
      } else {
        return null;
      }
    }

    private JComponent createProgressGridPanel() {
      JPanel panel = new JPanel();
      panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));

      JBPanelFactory pf = JBPanelFactory.getInstance();

      JProgressBar pb1 = new JProgressBar(0, 100);
      JProgressBar pb2 = new JProgressBar(0, 100);
      PanelGridBuilder<ProgressPanelBuilder> pbb = pf.createProgressPanelGridBuilder().
        add(pf.createProgressPanelBuilder(pb1).
          setLabelText("Label 1.1").
          setLabelLocation(SwingConstants.TOP).
          setCancelAction(()-> System.out.println("Cancel action #1"))).
        add(pf.createProgressPanelBuilder(pb2).
          setLabelText("Label 1.2").
          setLabelLocation(SwingConstants.TOP).
          setPauseAction(()-> System.out.println("Pause action #2")).
          setResumeAction(()-> System.out.println("Resume action #2"))).
        expandVertically(false);

      panel.add(pbb.createPanel());

      ObjectUtils.assertNotNull(ProgressPanel.forComponent(pb1)).setCommentText("Long long long long long long long text");
      ObjectUtils.assertNotNull(ProgressPanel.forComponent(pb2)).setCommentText("Short text");

      JProgressBar pb3 = new JProgressBar(0, 100);
      JProgressBar pb4 = new JProgressBar(0, 100);
      pbb = pf.createProgressPanelGridBuilder().
        add(pf.createProgressPanelBuilder(pb3).
          setTopSeparatorEnabled(true).
          setLabelText("Label 2.1").
          setLabelLocation(SwingConstants.LEFT).
          setCancelAction(()-> System.out.println("Cancel action #3"))).
        add(pf.createProgressPanelBuilder(pb4).
          setLabelText("Label 2.2").
          setLabelLocation(SwingConstants.LEFT).
          setPauseAction(()-> System.out.println("Pause action #4")).
          setResumeAction(()-> System.out.println("Resume action #4"))).
        expandVertically(false);

      panel.add(pbb.createPanel());
      ObjectUtils.assertNotNull(ProgressPanel.forComponent(pb3)).setCommentText("Long long long long long long text");
      ObjectUtils.assertNotNull(ProgressPanel.forComponent(pb4)).setCommentText("Short text");

      JProgressBar pb5 = new JProgressBar(0, 100);
      JProgressBar pb6 = new JProgressBar(0, 100);
      pbb = pf.createProgressPanelGridBuilder().
        add(pf.createProgressPanelBuilder(pb5).
          setTopSeparatorEnabled(true).
          setLabelText("Label 3.1").
          setLabelLocation(SwingConstants.LEFT).
          setCancelAction(()-> System.out.println("Cancel action #5")).
          setSmallVariant(true)).
        add(pf.createProgressPanelBuilder(pb6).
          setLabelText("Label 3.2").
          setLabelLocation(SwingConstants.LEFT).
          setPauseAction(()-> System.out.println("Pause action #6")).
          setResumeAction(()-> System.out.println("Resume action #6")).
          setSmallVariant(true)).
        expandVertically(false);

      panel.add(pbb.createPanel());
      ObjectUtils.assertNotNull(ProgressPanel.forComponent(pb5)).setCommentText("Long long long long long long text");
      ObjectUtils.assertNotNull(ProgressPanel.forComponent(pb6)).setCommentText("Short text");

      pbb = pf.createProgressPanelGridBuilder().
        add(pf.createProgressPanelBuilder(new JProgressBar(0, 100)).
          setTopSeparatorEnabled(true).
          setSmallVariant(true).
          setCommentEnabled(false).
          setLabelText("Label 4.1").
          setLabelLocation(SwingConstants.LEFT).
          setCancelAction(()-> System.out.println("Cancel action #7"))).
        add(pf.createProgressPanelBuilder(new JProgressBar(0, 100)).
          setSmallVariant(true).
          setCommentEnabled(false).
          setLabelText("Label 4.2").
          setLabelLocation(SwingConstants.LEFT).
          setPauseAction(()-> System.out.println("Pause action #8")).
          setResumeAction(()-> System.out.println("Resume action #8"))).
        expandVertically(false);

      panel.add(pbb.createPanel());

      pbb = pf.createProgressPanelGridBuilder().
        add(pf.createProgressPanelBuilder(new JProgressBar(0, 100)).
          setTopSeparatorEnabled(true).
          setCommentEnabled(false).
          setSmallVariant(true).
          setCancelAction(()-> System.out.println("Cancel action #9"))).
        expandVertically(false);

      panel.add(pbb.createPanel());

      pbb = pf.createProgressPanelGridBuilder().
        add(pf.createProgressPanelBuilder(new JProgressBar(0, 100)).
          setTopSeparatorEnabled(true).
          setCommentEnabled(false).
          setCancelAsButton(true).
          setCancelAction(()-> System.out.println("Cancel action #11"))).
        expandVertically(false);

      panel.add(pbb.createPanel());

      panel.add(Box.createVerticalBox());
      return panel;
    }
  }
}
