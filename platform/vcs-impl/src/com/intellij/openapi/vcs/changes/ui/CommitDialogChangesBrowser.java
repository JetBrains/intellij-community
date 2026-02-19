// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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


  public abstract @NotNull LocalChangeList getSelectedChangeList();


  public abstract @NotNull List<Change> getDisplayedChanges();

  public abstract @NotNull List<Change> getSelectedChanges();

  public abstract @NotNull List<Change> getIncludedChanges();

  public abstract @NotNull List<FilePath> getDisplayedUnversionedFiles();

  public abstract @NotNull List<FilePath> getSelectedUnversionedFiles();

  public abstract @NotNull List<FilePath> getIncludedUnversionedFiles();


  public abstract void updateDisplayedChangeLists();
}
