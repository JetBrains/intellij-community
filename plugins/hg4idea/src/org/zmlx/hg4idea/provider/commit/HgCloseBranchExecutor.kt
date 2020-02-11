// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.zmlx.hg4idea.provider.commit

import com.intellij.openapi.vcs.changes.CommitContext
import com.intellij.openapi.vcs.changes.CommitExecutorBase
import com.intellij.openapi.vcs.changes.CommitSession
import org.jetbrains.annotations.Nls
import org.zmlx.hg4idea.repo.HgRepository

class HgCloseBranchExecutor(private val myCheckinEnvironment: HgCheckinEnvironment) : CommitExecutorBase() {
  override fun areChangesRequired(): Boolean = false

  @Nls
  override fun getActionText(): String = "Commit And Close"

  override fun createCommitSession(commitContext: CommitContext): CommitSession {
    commitContext.isCloseBranch = true
    return CommitSession.VCS_COMMIT
  }

  fun setRepositories(repositories: Collection<HgRepository>) = myCheckinEnvironment.setRepos(repositories)
}