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
package com.intellij.execution.testframework.sm.runner.history.actions;

import com.intellij.execution.testframework.sm.runner.SMTRunnerConsoleProperties;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ImportTestsFromHistoryGroup extends ActionGroup {
  private SMTRunnerConsoleProperties myProperties;

  public ImportTestsFromHistoryGroup(SMTRunnerConsoleProperties properties) {
    super(properties == null ? "From History" : "Import From History", true);
    myProperties = properties;
  }

  @NotNull
  @Override
  public AnAction[] getChildren(@Nullable AnActionEvent e) {
    if (e == null) return EMPTY_ARRAY;
    final Project project = e.getProject();
    final String[] files = AbstractImportTestsAction.getTestHistoryRoot(project).list();
    if (files == null) return EMPTY_ARRAY;
    final AnAction[] actions = new AnAction[files.length];
    for (int i = 0; i < files.length; i++) {
      actions[i] = new ImportTestsFromHistoryAction(myProperties, files[i]);
    }
    return actions;
  }

  @Override
  public void update(AnActionEvent e) {
    e.getPresentation().setEnabledAndVisible(e.getProject() != null);
  }
}
