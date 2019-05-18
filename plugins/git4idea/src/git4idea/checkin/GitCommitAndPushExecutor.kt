// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.checkin

import com.intellij.openapi.vcs.changes.CommitExecutor
import com.intellij.openapi.vcs.changes.CommitSession
import org.jetbrains.annotations.Nls

class GitCommitAndPushExecutor(private val myCheckinEnvironment: GitCheckinEnvironment) : CommitExecutor {
  @Nls
  override fun getActionText(): String = "Commit and &Push..."

  override fun useDefaultAction(): Boolean = false

  override fun getId(): String = ID

  override fun supportsPartialCommit(): Boolean = true

  override fun createCommitSession(): CommitSession {
    myCheckinEnvironment.setNextCommitIsPushed(true)
    return CommitSession.VCS_COMMIT
  }

  companion object {
    internal const val ID = "Git.Commit.And.Push.Executor"
  }
}
