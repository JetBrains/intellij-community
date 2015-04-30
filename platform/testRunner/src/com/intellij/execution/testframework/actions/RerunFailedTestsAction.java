/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.execution.testframework.actions;

import com.intellij.execution.ExecutionManager;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

class RerunFailedTestsAction extends AnAction {
  @Override
  public void update(AnActionEvent e) {
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

    RunContentDescriptor contentDescriptor = ExecutionManager.getInstance(project).getContentManager().getSelectedContent();
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
