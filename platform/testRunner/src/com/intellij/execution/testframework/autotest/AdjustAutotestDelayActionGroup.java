// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.testframework.autotest;

import com.intellij.execution.testframework.TestRunnerBundle;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Dennis.Ushakov
 */
@ApiStatus.Internal
public class AdjustAutotestDelayActionGroup extends ActionGroup {
  public static final int MAX_DELAY = 10;

  public AdjustAutotestDelayActionGroup() {
    super(TestRunnerBundle.message("action.AdjustAutotestDelayActionGroup.set.autotest.delay.text"), true);
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    RunContentDescriptor descriptor = e.getData(LangDataKeys.RUN_CONTENT_DESCRIPTOR);
    boolean visible = false;
    if (descriptor != null) {
      for (AnAction action : descriptor.getRestartActions()) {
        if (action instanceof ToggleAutoTestAction) {
          visible = ((ToggleAutoTestAction)action).isDelayApplicable();
          break;
        }
      }
    }
    e.getPresentation().setVisible(visible);
  }

  @Override
  public AnAction @NotNull [] getChildren(@Nullable AnActionEvent e) {
    final AnAction[] actions = new AnAction[MAX_DELAY];
    for (int i = 0; i < MAX_DELAY; i++) {
      actions[i] = new SetAutoTestDelayAction(i + 1);
    }
    return actions;
  }

  private static class SetAutoTestDelayAction extends ToggleAction {
    private final int myDelay;

    SetAutoTestDelayAction(int delay) {
      super(TestRunnerBundle.message("action.seconds.text", delay));
      myDelay = delay * 1000;
    }

    @Override
    public boolean isSelected(@NotNull AnActionEvent e) {
      Project project = e.getProject();
      return project != null && AutoTestManager.getInstance(project).getDelay() == myDelay;
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.BGT;
    }

    @Override
    public void setSelected(@NotNull AnActionEvent e, boolean state) {
      Project project = e.getProject();
      if (project != null) {
        AutoTestManager.getInstance(project).setDelay(myDelay);
      }
    }
  }
}
