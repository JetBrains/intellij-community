// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.testframework.actions;

import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.execution.ui.RunContentManager;
import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ExecutionDataKeys;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

final class RerunFailedTestsAction extends AnAction {

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    e.getPresentation().setEnabled(getAction(e, false));
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    getAction(e, true);
  }

  private static boolean getAction(@NotNull AnActionEvent e, boolean execute) {
    Project project = e.getProject();
    if (project == null) {
      return false;
    }

    RunContentDescriptor contentDescriptor = RunContentManager.getInstance(project).getSelectedContent();
    if (contentDescriptor == null) {
      return false;
    }

    JComponent component = contentDescriptor.getComponent();
    if (component == null) {
      return false;
    }

    AnAction[] actions = contentDescriptor.getRestartActions();

    for (AnAction action : actions) {
      if (action instanceof AbstractRerunFailedTestsAction) {
        if (execute) {
          ExecutionEnvironment environment = ExecutionDataKeys.EXECUTION_ENVIRONMENT.getData(DataManager.getInstance().getDataContext(component));
          if (environment == null) {
            return false;
          }

          ((AbstractRerunFailedTestsAction)action).execute(e, environment);
          return true;
        }
        else {
          return ((AbstractRerunFailedTestsAction)action).isActive(e);
        }
      }
    }
    return false;
  }
}
