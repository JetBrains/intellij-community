// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.commit

import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.LocalChangeList

internal class ChangesViewCommitMessagePolicy(
  project: Project,
  commitMessageUi: CommitMessageUi,
  initialChangeList: LocalChangeList,
) : ChangeListCommitMessagePolicy(project, commitMessageUi, initialChangeList) {
  override val delayedMessagesProvidersSupport = object : DelayedMessageProvidersSupport {
    override fun saveCurrentCommitMessage() {
      saveMessageToChangeListDescription()
    }

    override fun restoredCommitMessage(): CommitMessage? = getCommitMessageForCurrentList()
  }

  override fun getNewMessageAfterCommit(): CommitMessage? = getCommitMessageFromProvider(currentChangeList)

  override fun dispose() {
    if (changeListManager.areChangeListsEnabled()) {
      saveMessageToChangeListDescription()
    }
    else {
      // Disposal of ChangesViewCommitWorkflowHandler on 'com.intellij.vcs.commit.CommitMode.ExternalCommitMode' enabling.
    }
  }
}