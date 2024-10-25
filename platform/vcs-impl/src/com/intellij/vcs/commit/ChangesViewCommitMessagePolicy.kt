// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.commit

import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.LocalChangeList

internal class ChangesViewCommitMessagePolicy(
  project: Project,
  commitMessageUi: CommitMessageUi,
  initialChangeList: LocalChangeList,
) : ChangeListCommitMessagePolicy(project, commitMessageUi, initialChangeList, true) {
  override fun getInitialMessage(): String? = getCommitMessage()

  override fun getMessageForNewChangeList(): String = getCommitMessage()

  override fun onAfterCommit() {
    commitMessageUi.text = getCommitMessage()
  }

  override fun dispose() {
    if (changeListManager.areChangeListsEnabled()) {
      val commitMessage = commitMessageUi.text
      editCurrentChangeListComment(commitMessage)
    }
    else {
      // Disposal of ChangesViewCommitWorkflowHandler on 'com.intellij.vcs.commit.CommitMode.ExternalCommitMode' enabling.
    }
  }

  private fun getCommitMessage(): String {
    if (clearInitialCommitMessage) return ""
    return getCommitMessageForCurrentList()?.takeIf { it.isNotBlank() }
           ?: vcsConfiguration.LAST_COMMIT_MESSAGE.orEmpty()
  }
}