// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.checkin;

import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.CommitContext;
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.List;

@ApiStatus.Experimental
public interface PostCommitChangeConverter {
  @NotNull
  @RequiresBackgroundThread
  List<Change> collectChangesAfterCommit(@NotNull CommitContext commitContext) throws VcsException;

  /**
   * @param commitContexts Contexts for multiple consequent local commits, in order.
   * @return whether commits were created one-after-another and can be analyzed as one.
   * If vcs branch was changed between the commits, only the last commit should be checked.
   */
  @RequiresBackgroundThread
  boolean areConsequentCommits(@NotNull List<CommitContext> commitContexts);

  @RequiresEdt
  boolean isFailureUpToDate(@NotNull List<CommitContext> commitContexts);
}
