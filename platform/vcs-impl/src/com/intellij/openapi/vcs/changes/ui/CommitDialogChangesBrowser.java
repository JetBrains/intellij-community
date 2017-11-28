/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.openapi.vcs.changes.ui;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.LocalChangeList;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public abstract class CommitDialogChangesBrowser extends ChangesBrowserBase implements Disposable {
  public CommitDialogChangesBrowser(@NotNull Project project,
                                    boolean showCheckboxes,
                                    boolean highlightProblems) {
    super(project, showCheckboxes, highlightProblems);
  }

  @Override
  public void dispose() {
  }


  @NotNull
  public abstract LocalChangeList getSelectedChangeList();


  @NotNull
  public abstract List<Change> getDisplayedChanges();

  @NotNull
  public abstract List<Change> getSelectedChanges();

  @NotNull
  public abstract List<Change> getIncludedChanges();

  @NotNull
  public abstract List<VirtualFile> getDisplayedUnversionedFiles();

  @NotNull
  public abstract List<VirtualFile> getSelectedUnversionedFiles();

  @NotNull
  public abstract List<VirtualFile> getIncludedUnversionedFiles();


  public abstract void updateDisplayedChangeLists();
}
