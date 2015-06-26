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
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Separator;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Arrays;
import java.util.Comparator;

public class ImportTestsGroup extends ActionGroup {
  private SMTRunnerConsoleProperties myProperties;
  public ImportTestsGroup() {
    super("Import Test Results", "Import Test Results", AllIcons.Vcs.History);
    setPopup(true);
  }

  public ImportTestsGroup(SMTRunnerConsoleProperties properties) {
    this();
    myProperties = properties;
  }

  @NotNull
  @Override
  public AnAction[] getChildren(@Nullable AnActionEvent e) {
    if (e == null) return EMPTY_ARRAY;
    final Project project = e.getProject();
    final File[] files = AbstractImportTestsAction.getTestHistoryRoot(project).listFiles();
    if (files == null) return EMPTY_ARRAY;
    Arrays.sort(files, new Comparator<File>() {
      @Override
      public int compare(File f1, File f2) {
        return f1.lastModified() > f2.lastModified() ? -1 : 1;
      }
    });
    final AnAction[] actions = new AnAction[files.length + 2];
    for (int i = 0; i < files.length; i++) {
      actions[i] = new ImportTestsFromHistoryAction(myProperties, files[i].getName());
    }
    actions[files.length] = Separator.getInstance();
    actions[files.length + 1] = new ImportTestsFromFileAction(myProperties); 
    return actions;
  }

  @Override
  public void update(AnActionEvent e) {
    e.getPresentation().setEnabledAndVisible(e.getProject() != null);
  }
}
