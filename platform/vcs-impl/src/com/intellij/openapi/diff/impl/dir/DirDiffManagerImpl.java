/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.openapi.diff.impl.dir;

import com.intellij.ide.diff.DiffElement;
import com.intellij.ide.diff.DirDiffSettings;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diff.DirDiffManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import org.jetbrains.annotations.NotNull;

/**
 * @author Konstantin Bulenkov
 */
public class DirDiffManagerImpl extends DirDiffManager {
  private final Project myProject;

  public DirDiffManagerImpl(Project project) {
    myProject = project;
  }

  @Override
  public void showDiff(@NotNull final DiffElement dir1, @NotNull final DiffElement dir2, final DirDiffSettings settings) {
    Task.Backgroundable task = new Task.Backgroundable(myProject, "Directory comparison", true) {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        indicator.setText("Calculating differences");
        final DirDiffTableModel model = new DirDiffTableModel(myProject, dir1, dir2, indicator, settings);
        final Runnable run = new Runnable() {
          public void run() {
            if (model.getRowCount() == 0) {
              Messages.showInfoMessage(myProject, "No difference has been found", "Directory Diff Tool");
            } else {
              new DirDiffDialog(myProject, model, settings).show();
            }
          }
        };
        ApplicationManager.getApplication().invokeLater(run);
      }
    };
    ProgressManager.getInstance().run(task);
  }

  @Override
  public boolean canShow(@NotNull DiffElement dir1, @NotNull DiffElement dir2) {
    return dir1.isContainer() && dir2.isContainer();
  }
}
