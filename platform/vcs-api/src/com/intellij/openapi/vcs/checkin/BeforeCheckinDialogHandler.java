// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.checkin;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.CommitExecutor;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Allows validating commit conditions before starting commit flow. Only used for Commit Dialog.
 * E.g. could be used to check if remote server is available when performing commit to Centralized VCS.
 */
public abstract class BeforeCheckinDialogHandler {
  /**
   * Checks if commit conditions are valid and Commit Dialog should be shown.
   *
   * @param project       project where commit is performed
   * @param changes       changes to commit
   * @param executors     custom commit executors available for commit
   * @param showVcsCommit {@code true} if usual VCS commit is available in Commit Dialog.
   *                      {@code false} if only custom commit executors could be used.
   * @return {@code true} if commit conditions are valid and Commit Dialog should be shown. {@code false} otherwise.
   */
  public boolean beforeCommitDialogShown(@NotNull Project project,
                                         @NotNull List<Change> changes,
                                         @NotNull Iterable<CommitExecutor> executors,
                                         boolean showVcsCommit) {
    throw new AbstractMethodError();
  }
}
