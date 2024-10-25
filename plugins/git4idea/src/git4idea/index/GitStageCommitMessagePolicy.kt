// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.index

import com.intellij.openapi.project.Project
import com.intellij.vcs.commit.AbstractCommitMessagePolicy
import com.intellij.vcs.commit.CommitMessage
import com.intellij.vcs.commit.CommitMessageUi

class GitStageCommitMessagePolicy(
  project: Project,
  commitMessageUi: CommitMessageUi,
) : AbstractCommitMessagePolicy(project, commitMessageUi) {
  override fun getInitialMessage(): CommitMessage? =
    getCommitMessageFromProvider(changeList = null)
    ?: vcsConfiguration.LAST_COMMIT_MESSAGE?.let { CommitMessage(it) }

  override val delayedMessagesProvidersSupport = object : DelayedMessageProvidersSupport {
    override fun saveCurrentCommitMessage() {
      saveCommitMessage()
    }

    override fun restoredCommitMessage(): CommitMessage? = getInitialMessage()
  }

  override fun getNewMessageAfterCommit(): CommitMessage? =
    getCommitMessageFromProvider(changeList = null)

  override fun cleanupStoredMessage() {
    vcsConfiguration.LAST_COMMIT_MESSAGE = ""
  }

  override fun dispose() {
    saveCommitMessage()
  }

  private fun saveCommitMessage() {
    if (!currentMessageIsDisposable) {
      vcsConfiguration.LAST_COMMIT_MESSAGE = commitMessageUi.text
    }
  }
}
