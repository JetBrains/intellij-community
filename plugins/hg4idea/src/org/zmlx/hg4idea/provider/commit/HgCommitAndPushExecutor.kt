// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.zmlx.hg4idea.provider.commit

import com.intellij.dvcs.commit.getCommitAndPushActionName
import com.intellij.dvcs.ui.DvcsBundle
import com.intellij.openapi.util.Key
import com.intellij.openapi.vcs.changes.CommitContext
import com.intellij.openapi.vcs.changes.CommitExecutorWithRichDescription
import com.intellij.openapi.vcs.changes.CommitSession
import com.intellij.vcs.commit.CommitWorkflowHandlerState
import com.intellij.vcs.commit.commitProperty
import org.jetbrains.annotations.Nls

private val IS_PUSH_AFTER_COMMIT_KEY = Key.create<Boolean>("Hg.Commit.IsPushAfterCommit")
internal var CommitContext.isPushAfterCommit: Boolean by commitProperty(IS_PUSH_AFTER_COMMIT_KEY)

class HgCommitAndPushExecutor : CommitExecutorWithRichDescription {
  @Nls
  override fun getActionText(): String = DvcsBundle.message("action.commit.and.push.text")

  override fun getText(state: CommitWorkflowHandlerState): String {
    return getCommitAndPushActionName(state)
  }

  override fun useDefaultAction(): Boolean = false

  override fun requiresSyncCommitChecks(): Boolean = true

  override fun getId(): String = ID

  override fun createCommitSession(commitContext: CommitContext): CommitSession {
    commitContext.isPushAfterCommit = true
    return CommitSession.VCS_COMMIT
  }

  companion object {
    internal const val ID = "Hg.Commit.And.Push.Executor"
  }
}
