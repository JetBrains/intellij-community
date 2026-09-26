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

    override fun restoredCommitMessage(): CommitMessage? = getCommitMessageFromChangelistDescription()
  }

  override fun getNewCommitMessage(): CommitMessage? = getCommitMessageForCurrentList()
}