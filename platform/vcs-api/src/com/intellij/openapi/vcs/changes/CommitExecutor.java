// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.extensions.ProjectExtensionPointName;
import com.intellij.vcs.commit.CommitWorkflowHandler;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Use {@link #LOCAL_COMMIT_EXECUTOR} extension point to register executor for local changes.
 * Use {@link com.intellij.openapi.vcs.changes.ui.CommitChangeListDialog#commitChanges} to show modal commit dialog for a given executor.
 * Implement {@link LocalCommitExecutor} for executors that should be ignored by pre-commit checks (ex: "Create Patch" executor).
 */
public interface CommitExecutor {
  /**
   * Allows registering additional commit actions for local changes.
   *
   * @see ChangeListManager#registerCommitExecutor(CommitExecutor)
   * @see com.intellij.openapi.vcs.changes.actions.CommitExecutorAction
   */
  ProjectExtensionPointName<CommitExecutor> LOCAL_COMMIT_EXECUTOR =
    new ProjectExtensionPointName<>("com.intellij.vcs.changes.localCommitExecutor");

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
  default @Nullable @NonNls String getId() {
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
  default @NotNull CommitSession createCommitSession() {
    throw new AbstractMethodError();
  }

  /**
   * Prepare for the commit operation.
   * Return {@link CommitSession#VCS_COMMIT} to delegate to the 'default' VCS commit.
   */
  default @NotNull CommitSession createCommitSession(@NotNull CommitContext commitContext) {
    return createCommitSession();
  }
}
