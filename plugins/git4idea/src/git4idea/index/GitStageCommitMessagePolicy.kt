// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.index

import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.ChangeListManager.getInstance
import com.intellij.vcs.commit.AbstractCommitMessagePolicy
import com.intellij.vcs.commit.CommitMessageUi

class GitStageCommitMessagePolicy(
  project: Project,
  commitMessageUi: CommitMessageUi,
) : AbstractCommitMessagePolicy(project, commitMessageUi, true) {
  override fun getInitialMessage(): String? {
    return getCommitMessageFromProvider() ?: vcsConfiguration.LAST_COMMIT_MESSAGE.orEmpty()
  }

  override fun getNewMessageAfterCommit(): String? = getCommitMessageFromProvider()

  private fun getCommitMessageFromProvider(): String? {
    val defaultChangeList = getInstance(project).defaultChangeList // always blank, required for 'CommitMessageProvider'
    return getCommitMessageFromProvider(defaultChangeList)
  }

  override fun cleanupStoredMessage() {
    vcsConfiguration.LAST_COMMIT_MESSAGE = ""
  }

  override fun dispose() {
    vcsConfiguration.LAST_COMMIT_MESSAGE = commitMessageUi.text
  }
}
