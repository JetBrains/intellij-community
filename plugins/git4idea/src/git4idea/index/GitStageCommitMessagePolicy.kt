// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.index

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.intellij.vcs.commit.AbstractCommitMessagePolicy
import com.intellij.vcs.commit.CommitMessageUi

class GitStageCommitMessagePolicy(project: Project,
                                  private val commitMessageUi: CommitMessageUi) : AbstractCommitMessagePolicy(project) {
  fun init(disposable: Disposable) {
    listenForDelayedProviders(commitMessageUi, disposable)

    if (vcsConfiguration.CLEAR_INITIAL_COMMIT_MESSAGE) {
      commitMessageUi.text = ""
      return
    }

    commitMessageUi.text = vcsConfiguration.LAST_COMMIT_MESSAGE.orEmpty()
  }

  fun onBeforeCommit() {
    val commitMessage = commitMessageUi.text
    vcsConfiguration.saveCommitMessage(commitMessage)
  }

  fun onAfterCommit() {
    if (vcsConfiguration.CLEAR_INITIAL_COMMIT_MESSAGE) {
      commitMessageUi.text = ""
      return
    }

    val defaultChangeList = ChangeListManager.getInstance(project).defaultChangeList // always blank, required for 'CommitMessageProvider'
    commitMessageUi.text = getCommitMessageFromProvider(defaultChangeList)
                           ?: vcsConfiguration.LAST_COMMIT_MESSAGE
  }
}
