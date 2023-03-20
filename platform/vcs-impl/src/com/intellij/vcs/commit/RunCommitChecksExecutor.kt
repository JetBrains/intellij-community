// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.commit

import com.intellij.idea.ActionsBundle
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.NlsActions
import com.intellij.openapi.vcs.changes.CommitContext
import com.intellij.openapi.vcs.changes.CommitExecutor
import com.intellij.openapi.vcs.changes.CommitSession
import com.intellij.openapi.vcs.changes.actions.BaseCommitExecutorAction

private val IS_ONLY_RUN_COMMIT_CHECKS_KEY = Key.create<Boolean>("Vcs.Commit.IsOnlyRunCommitChecks")
internal var CommitContext.isOnlyRunCommitChecks: Boolean by commitExecutorProperty(IS_ONLY_RUN_COMMIT_CHECKS_KEY)

internal object RunCommitChecksExecutor : CommitExecutor {
  const val ID = "Vcs.RunCommitChecks.Executor"

  override fun getId(): String = ID

  override fun getActionText(): @NlsActions.ActionText String = ActionsBundle.message("action.Vcs.RunCommitChecks.text")
  override fun useDefaultAction(): Boolean = false

  override fun createCommitSession(commitContext: CommitContext): CommitSession {
    commitContext.isOnlyRunCommitChecks = true
    return CommitSession.VCS_COMMIT
  }
}

internal class RunCommitChecksAction : BaseCommitExecutorAction() {
  override val executorId: String get() = RunCommitChecksExecutor.ID
}