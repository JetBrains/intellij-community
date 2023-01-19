// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.commit

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.LocalChangeList
import com.intellij.ui.TextAccessor

internal class ChangesViewCommitMessagePolicy(project: Project,
                                              private val includedChanges: () -> List<Change>) : AbstractCommitMessagePolicy(project) {
  fun init(changeList: LocalChangeList): String? {
    return getCommitMessage(changeList)
  }

  fun onChangelistChanged(oldChangeList: LocalChangeList, newChangeList: LocalChangeList, currentMessage: TextAccessor) {
    changeListManager.editComment(oldChangeList.name, currentMessage.text)

    currentMessage.text = getCommitMessage(newChangeList)
  }

  fun onBeforeCommit(changeList: LocalChangeList, commitMessage: String) {
    vcsConfiguration.saveCommitMessage(commitMessage)
    changeListManager.editComment(changeList.name, commitMessage)
  }

  fun onAfterCommit(changeList: LocalChangeList, currentMessage: TextAccessor) {
    currentMessage.text = getCommitMessage(changeList)
  }

  fun onDispose(changeList: LocalChangeList, commitMessage: String) {
    if (changeListManager.areChangeListsEnabled()) {
      changeListManager.editComment(changeList.name, commitMessage)
    }
    else {
      // Disposal of ChangesViewCommitWorkflowHandler on 'com.intellij.vcs.commit.CommitMode.ExternalCommitMode' enabling.
    }
  }

  private fun getCommitMessage(changeList: LocalChangeList): String? {
    if (vcsConfiguration.CLEAR_INITIAL_COMMIT_MESSAGE) return null
    return getCommitMessageForList(changeList)?.takeIf { it.isNotBlank() }
           ?: getCommitMessageFromVcs(includedChanges())
           ?: vcsConfiguration.LAST_COMMIT_MESSAGE
  }

  companion object {
    private val LOG = logger<ChangesViewCommitMessagePolicy>()
  }
}