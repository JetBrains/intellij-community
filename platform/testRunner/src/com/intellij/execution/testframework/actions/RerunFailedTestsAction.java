// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.testframework.actions;

import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.execution.ui.RunContentManager;
import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

class RerunFailedTestsAction extends AnAction {
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

    ExecutionEnvironment environment = LangDataKeys.EXECUTION_ENVIRONMENT.getData(DataManager.getInstance().getDataContext(component));
    if (environment == null) {
      return false;
    }

    AnAction[] actions = contentDescriptor.getRestartActions();
    if (actions.length == 0) {
      return false;
    }

    for (AnAction action : actions) {
      if (action instanceof AbstractRerunFailedTestsAction) {
        if (execute) {
          ((AbstractRerunFailedTestsAction)action).execute(e, environment);
        }
        return true;
      }
    }
    return false;
  }
}
