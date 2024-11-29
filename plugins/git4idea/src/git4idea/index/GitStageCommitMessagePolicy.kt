// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.index

import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.intellij.vcs.commit.AbstractCommitMessagePolicy
import com.intellij.vcs.commit.CommitMessage
import com.intellij.vcs.commit.CommitMessageUi

internal class GitStageCommitMessagePolicy(
  project: Project,
  commitMessageUi: CommitMessageUi,
) : AbstractCommitMessagePolicy(project, commitMessageUi) {
  override fun getNewCommitMessage(): CommitMessage? {
    return getCommitMessageFromProvider() ?: getPersistedCommitMessage()
  }

  override val delayedMessagesProvidersSupport = object : DelayedMessageProvidersSupport {
    override fun saveCurrentCommitMessage() {
      saveCommitMessage()
    }

    override fun restoredCommitMessage(): CommitMessage? = getPersistedCommitMessage()
  }

  private fun getCommitMessageFromProvider(): CommitMessage? {
    val changeList = ChangeListManager.getInstance(project).defaultChangeList // always blank, required for 'CommitMessageProvider'
    return getCommitMessageFromProvider(project, changeList)
  }

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

  private fun getPersistedCommitMessage(): CommitMessage? = vcsConfiguration.LAST_COMMIT_MESSAGE?.let { CommitMessage(it) }
}
