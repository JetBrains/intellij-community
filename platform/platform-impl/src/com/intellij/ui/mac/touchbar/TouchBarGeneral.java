// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.mac.touchbar;

import com.intellij.execution.*;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.util.messages.MessageBus;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class TouchBarGeneral extends TouchBarActionBase {
  private TBItemButton    myButtonAddRunConf;

  private TBItemPopover   myPopoverRunConf;
  private TouchBar        myPopoverRunConfExpandTB;
  private TBItemButton    myPopoverRunConfTapAndHold;
  private TBItemScrubber  myScrubberRunConf;

  private TBItemButton            myButtonRun;
  private TBItemAnActionButton    myButtonDebug;
  private TBItemAnActionButton    myButtonStop;

  TouchBarGeneral(@NotNull Project project, Component component, String desc) {
    super("general_"+desc, project, component);

    addAnActionButton("CompileDirty", false); // NOTE: IdeActions.ACTION_COMPILE doesn't work

    myButtonAddRunConf = addButton(AllIcons.General.Add, "Add Configuration", IdeActions.ACTION_EDIT_RUN_CONFIGURATIONS);

    {
      final TouchBar tapHoldTB = new TouchBar("run_configs_popover_tap_and_hold", false);
      tapHoldTB.addFlexibleSpacing();
      myPopoverRunConfTapAndHold = tapHoldTB.addButton(null, null, (NSTLibrary.Action)null);
      tapHoldTB.selectVisibleItemsToShow();

      myPopoverRunConfExpandTB = new TouchBar("run_configs_popover_expand", false);
      myPopoverRunConfExpandTB.addButton(AllIcons.Actions.EditSource, null, IdeActions.ACTION_EDIT_RUN_CONFIGURATIONS);
      myScrubberRunConf = myPopoverRunConfExpandTB.addScrubber(500);
      myPopoverRunConfExpandTB.addFlexibleSpacing();
      myPopoverRunConfExpandTB.selectVisibleItemsToShow();

      myPopoverRunConf = addPopover(null, null, 143, myPopoverRunConfExpandTB, tapHoldTB);
    }

    {
      myButtonRun = addButton();
      myButtonDebug = addAnActionButton(IdeActions.ACTION_DEFAULT_DEBUGGER);
      myButtonDebug.setAutoVisibility(false);
      myButtonStop = addAnActionButton(IdeActions.ACTION_STOP_PROGRAM);
      myButtonStop.setAutoVisibility(false);
    }

    addSpacing(true);
    addAnActionButton("Vcs.UpdateProject", false);  // NOTE: IdeActions.ACTION_CVS_CHECKOUT doesn't works
    addAnActionButton("CheckinProject", false);     // NOTE: IdeActions.ACTION_CVS_COMMIT doesn't works

    _updateRunConfigs();
    _updateRunButtons();

    final MessageBus mb = myProject.getMessageBus();

    mb.connect().subscribe(RunManagerListener.TOPIC, new RunManagerListener() {
      @Override
      public void runConfigurationSelected() {
        _updateSelectedConf();
      }
      @Override
      public void runConfigurationAdded(@NotNull RunnerAndConfigurationSettings settings) {
        _updateRunConfigs();
      }
      @Override
      public void runConfigurationRemoved(@NotNull RunnerAndConfigurationSettings settings) {
        _updateRunConfigs();
      }
    });
  }

  @Override
  public void processStarted(@NotNull String executorId, @NotNull ExecutionEnvironment env, @NotNull ProcessHandler handler) {
    super.processStarted(executorId, env, handler);
    _updateRunButtons();
  }
  @Override
  public void processTerminated(@NotNull String executorId, @NotNull ExecutionEnvironment env, @NotNull ProcessHandler handler, int exitCode) {
    super.processTerminated(executorId, env, handler, exitCode);
    ApplicationManager.getApplication().invokeLater(()->_updateRunButtons());
  }

  private void _updateSelectedConf() {
    final Icon icon;
    final String text;
    final RunManager rm = RunManager.getInstance(myProject);
    final RunnerAndConfigurationSettings selected = rm.getSelectedConfiguration();
    if (selected != null) {
      icon = selected.getConfiguration().getIcon();
      text = selected.getName();
    } else {
      icon = null;
      text = "Select configuration";
    }

    myPopoverRunConf.update(icon, text);
    myPopoverRunConfTapAndHold.update(icon, text);
  }

  private void _updateRunConfigs() {
    final RunManager rm = RunManager.getInstance(myProject);
    final List<RunnerAndConfigurationSettings> allRunCongigs = rm.getAllSettings();
    if (allRunCongigs == null || allRunCongigs.isEmpty()) {
      myButtonAddRunConf.setVisible(true);
      myPopoverRunConf.setVisible(false);
      myButtonRun.setVisible(false);
      myButtonDebug.setVisible(false);
      myButtonStop.setVisible(false);
    } else {
      myButtonAddRunConf.setVisible(false);
      myPopoverRunConf.setVisible(true);
      myButtonRun.setVisible(true);
      myButtonDebug.setVisible(true);
      myButtonStop.setVisible(true);

      _updateSelectedConf();

      List<TBItemScrubber.ItemData> scrubberItems = new ArrayList<>();
      for (RunnerAndConfigurationSettings rc : allRunCongigs) {
        final Icon iconRc = rc.getConfiguration().getIcon();
        scrubberItems.add(new TBItemScrubber.ItemData(iconRc, rc.getName(), () -> {
          // NOTE: executed at AppKit-thread
          ApplicationManager.getApplication().invokeLater(()->{ rm.setSelectedConfiguration(rc); });
          myPopoverRunConf.dismiss();
        }));
      }
      myScrubberRunConf.setItems(scrubberItems);
    }
    selectVisibleItemsToShow();
  }

  private static boolean _canBeStopped(ProcessHandler ph) {
    return ph != null && !ph.isProcessTerminated()
           && (!ph.isProcessTerminating() || ph instanceof KillableProcess && ((KillableProcess)ph).canKillProcess());
  }

  private void _updateRunButtons() {
    if (!myButtonRun.isVisible())
      return;

    if (myProject.isDisposed())
      return;

    final ExecutionManager em = ExecutionManager.getInstance(myProject);
    final ProcessHandler[] allRunning = em.getRunningProcesses();

    int runningCount = 0;
    for (ProcessHandler ph : allRunning)
      if (_canBeStopped(ph))
        ++runningCount;

    if (runningCount == 0)
      myButtonRun.update(AllIcons.Actions.Execute, null, new PlatformAction(IdeActions.ACTION_DEFAULT_RUNNER));
    else
      myButtonRun.update(AllIcons.Actions.Restart, null, new PlatformAction(IdeActions.ACTION_RERUN));
  }
}
