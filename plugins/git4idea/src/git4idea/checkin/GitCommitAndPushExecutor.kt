// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.checkin

import com.intellij.dvcs.ui.DvcsBundle
import com.intellij.openapi.components.Service
import com.intellij.openapi.util.Key
import com.intellij.openapi.vcs.changes.CommitContext
import com.intellij.openapi.vcs.changes.CommitExecutor
import com.intellij.openapi.vcs.changes.CommitSession
import com.intellij.vcs.commit.commitProperty
import org.jetbrains.annotations.Nls

private val IS_PUSH_AFTER_COMMIT_KEY = Key.create<Boolean>("Git.Commit.IsPushAfterCommit")
internal var CommitContext.isPushAfterCommit: Boolean by commitProperty(IS_PUSH_AFTER_COMMIT_KEY)

@Service(Service.Level.PROJECT)
internal class GitCommitAndPushExecutor : CommitExecutor {
  @Nls
  override fun getActionText(): String = DvcsBundle.message("action.commit.and.push.text")

  override fun useDefaultAction(): Boolean = false

  override fun getId(): String = ID

  override fun supportsPartialCommit(): Boolean = true

  override fun createCommitSession(commitContext: CommitContext): CommitSession {
    commitContext.isPushAfterCommit = true
    return CommitSession.VCS_COMMIT
  }

  companion object {
    internal const val ID = "Git.Commit.And.Push.Executor"
  }
}
