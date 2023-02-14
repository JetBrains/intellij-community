// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.openapi.vcs.changes;

import com.intellij.vcs.commit.CommitWorkflowHandler;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Use {@link LocalCommitExecutor} extension point to register executor for local changes.
 * Use {@link com.intellij.openapi.vcs.changes.ui.CommitChangeListDialog#commitChanges} to show modal commit dialog for a given executor.
 */
public interface CommitExecutor {
  @Nls
  @NotNull
  String getActionText();

  /**
   * Return 'true' if default action should be added to the commit panel for this executor.
   *
   * @see com.intellij.openapi.vcs.changes.actions.CommitExecutorAction
   */
  default boolean useDefaultAction() {
    return true;
  }

  /**
   * @see CommitWorkflowHandler#getExecutor(String)
   * @see com.intellij.openapi.vcs.changes.actions.BaseCommitExecutorAction
   */
  @Nullable
  @NonNls
  default String getId() {
    return null;
  }

  /**
   * Whether executor can be run without local changes.
   */
  default boolean areChangesRequired() {
    return true;
  }

  /**
   * Whether executor can handle committing of a part of a file.
   *
   * @see com.intellij.openapi.vcs.impl.PartialChangesUtil#processPartialChanges
   * @see com.intellij.openapi.vcs.ex.PartialLocalLineStatusTracker#handlePartialCommit
   */
  default boolean supportsPartialCommit() {
    return false;
  }

  /**
   * Whether pre-commit checks {@link com.intellij.openapi.vcs.checkin.CheckinHandler} and {@link com.intellij.openapi.vcs.checkin.CommitCheck}
   * need to be fully completed before actual commit via {@link CommitSession#execute}.
   *
   * @see com.intellij.openapi.vcs.checkin.CommitCheck.ExecutionOrder#LATE
   */
  default boolean requiresSyncCommitChecks() {
    return false;
  }

  /**
   * @deprecated Prefer overriding {@link #createCommitSession(CommitContext)}
   */
  @Deprecated
  @NotNull
  default CommitSession createCommitSession() {
    throw new AbstractMethodError();
  }

  /**
   * Prepare for the commit operation.
   * Return {@link CommitSession#VCS_COMMIT} to delegate to the 'default' VCS commit.
   */
  @NotNull
  default CommitSession createCommitSession(@NotNull CommitContext commitContext) {
    return createCommitSession();
  }
}
