// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.mac.touchbar;

import com.intellij.execution.ExecutionListener;
import com.intellij.execution.ExecutionManager;
import com.intellij.execution.RunManager;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;
import com.intellij.ui.mac.foundation.Foundation;
import com.intellij.ui.mac.foundation.ID;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TouchBarGeneral extends TouchBar {
  private static final Map<Project, TouchBarGeneral> ourInstances = new HashMap<>();

  private TBItemButton myButtonStop = null;

  private TouchBarGeneral(Project project) {
    super("general");

    addButton(AllIcons.Toolwindows.ToolWindowBuild, null, "CompileDirty"); // NOTE: IdeActions.ACTION_COMPILE doesn't work

    final RunManager rm = RunManager.getInstance(project);
    final RunnerAndConfigurationSettings selected = rm.getSelectedConfiguration();
    if (selected != null) {
      final TouchBar tapHoldTB = new TouchBar("run_configs_popover_tap_and_hold");
      final TouchBar expandTB = new TouchBar("run_configs_popover_expand");
      final TBItemPopover popover = addPopover(selected.getConfiguration().getIcon(), selected.getName(), 143, expandTB, tapHoldTB);

      tapHoldTB.addFlexibleSpacing();
      tapHoldTB.addButton(selected.getConfiguration().getIcon(), selected.getName(), (NSTLibrary.Action)null);
      tapHoldTB.selectAllItemsToShow();

      expandTB.addButton(AllIcons.Actions.EditSource, null, IdeActions.ACTION_EDIT_RUN_CONFIGURATIONS);
      final TBItemScrubber scrubber = expandTB.addScrubber(500);
      List<RunnerAndConfigurationSettings> allRunCongigs = rm.getAllSettings();
      for (RunnerAndConfigurationSettings rc : allRunCongigs) {
        final Icon iconRc = rc.getConfiguration().getIcon();
        scrubber.addItem(iconRc, rc.getName(), () -> {
          rm.setSelectedConfiguration(rc);
          popover.update(iconRc, rc.getName());
          popover.dismiss();
        });
      }
      expandTB.addFlexibleSpacing();
      expandTB.selectAllItemsToShow();

      addButton(AllIcons.Toolwindows.ToolWindowRun, null, IdeActions.ACTION_DEFAULT_RUNNER);
      addButton(AllIcons.Toolwindows.ToolWindowDebugger, null, IdeActions.ACTION_DEFAULT_DEBUGGER);
      myButtonStop = addButton(IconLoader.getDisabledIcon(AllIcons.Actions.Suspend), null, (NSTLibrary.Action)null);
    }

    addSpacing(true);
    addButton(AllIcons.Actions.CheckOut, null, new PlatformAction("Vcs.UpdateProject"));  // NOTE: IdeActions.ACTION_CVS_CHECKOUT doesn't works
    addButton(AllIcons.Actions.Commit, null, new PlatformAction("CheckinProject"));       // NOTE: IdeActions.ACTION_CVS_COMMIT doesn't works

    selectAllItemsToShow();

    ApplicationManager.getApplication().getMessageBus().connect().subscribe(ExecutionManager.EXECUTION_TOPIC, new ExecutionListener() {
      @Override
      public void processStarted(@NotNull String executorId, @NotNull ExecutionEnvironment env, @NotNull ProcessHandler handler) {
        myButtonStop.update(AllIcons.Actions.Suspend, null, new PlatformAction("Stop"));
        // TODO: implement other items update
      }
      @Override
      public void processTerminated(@NotNull String executorId, @NotNull ExecutionEnvironment env, @NotNull ProcessHandler handler, int exitCode) {
        myButtonStop.update(IconLoader.getDisabledIcon(AllIcons.Actions.Suspend), null, (NSTLibrary.Action)null);
        // TODO: implement other items update
      }
    });
  }

  public static @NotNull TouchBarGeneral instance(@NotNull Project project) {
    // NOTE: called from EDT only
    TouchBarGeneral result = ourInstances.get(project);
    if (result == null) {
      final ID pool = Foundation.invoke("NSAutoreleasePool", "new");
      try {
        result = new TouchBarGeneral(project);
        ourInstances.put(project, result);
      } finally {
        Foundation.invoke(pool, "release");
      }
    }
    return result;
  }

  public static void release(@NotNull Project project) {
    // NOTE: called from EDT only
    TouchBarGeneral result = ourInstances.remove(project);
    if (result != null)
      result.release();
  }
}
