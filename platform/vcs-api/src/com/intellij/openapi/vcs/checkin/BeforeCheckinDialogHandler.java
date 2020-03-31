// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.checkin;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.CommitExecutor;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public abstract class BeforeCheckinDialogHandler {
  /**
   * @return false to cancel commit
   */
  public boolean beforeCommitDialogShown(@NotNull Project project,
                                         @NotNull List<Change> changes,
                                         @NotNull Iterable<CommitExecutor> executors,
                                         boolean showVcsCommit) {
    throw new AbstractMethodError();
  }
}
