// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.ui;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.LocalChangeList;
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
  public abstract List<FilePath> getDisplayedUnversionedFiles();

  @NotNull
  public abstract List<FilePath> getSelectedUnversionedFiles();

  @NotNull
  public abstract List<FilePath> getIncludedUnversionedFiles();


  public abstract void updateDisplayedChangeLists();
}
