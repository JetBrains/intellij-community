// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.zmlx.hg4idea.provider.commit

import com.intellij.openapi.vcs.VcsRoot
import com.intellij.openapi.vcs.changes.CommitContext
import com.intellij.openapi.vcs.changes.CommitExecutor
import com.intellij.openapi.vcs.changes.CommitSession
import com.intellij.vcs.commit.commitWithoutChangesRoots
import org.jetbrains.annotations.Nls
import org.zmlx.hg4idea.HgBundle
import org.zmlx.hg4idea.repo.HgRepository

class HgCloseBranchExecutor(private val repositories: Collection<HgRepository>) : CommitExecutor {
  override fun areChangesRequired(): Boolean = false

  @Nls
  override fun getActionText(): String = HgBundle.message("action.hg4idea.CommitAndClose")

  override fun createCommitSession(commitContext: CommitContext): CommitSession {
    commitContext.isCloseBranch = true
    commitContext.commitWithoutChangesRoots = repositories.map { VcsRoot(it.vcs, it.root) }
    return CommitSession.VCS_COMMIT
  }
}