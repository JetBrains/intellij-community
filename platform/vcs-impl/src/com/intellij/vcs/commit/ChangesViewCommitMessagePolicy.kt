// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.commit

import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.LocalChangeList

internal class ChangesViewCommitMessagePolicy(project: Project,
                                              private val commitMessageUi: CommitMessageUi,
                                              private val includedChanges: () -> List<Change>) : AbstractCommitMessagePolicy(project) {
  fun init(changeList: LocalChangeList, disposable: Disposable) {
    listenForDelayedProviders(commitMessageUi, disposable)

    commitMessageUi.text = getCommitMessage(changeList)
  }

  fun onChangelistChanged(oldChangeList: LocalChangeList, newChangeList: LocalChangeList) {
    val commitMessage = commitMessageUi.text
    changeListManager.editComment(oldChangeList.name, commitMessage)
    vcsConfiguration.saveCommitMessage(commitMessage)

    commitMessageUi.text = getCommitMessage(newChangeList)
  }

  fun onBeforeCommit(changeList: LocalChangeList) {
    val commitMessage = commitMessageUi.text
    vcsConfiguration.saveCommitMessage(commitMessage)
    changeListManager.editComment(changeList.name, commitMessage)
  }

  fun onAfterCommit(changeList: LocalChangeList) {
    commitMessageUi.text = getCommitMessage(changeList)
  }

  fun onDispose(changeList: LocalChangeList) {
    if (changeListManager.areChangeListsEnabled()) {
      val commitMessage = commitMessageUi.text
      changeListManager.editComment(changeList.name, commitMessage)
    }
    else {
      // Disposal of ChangesViewCommitWorkflowHandler on 'com.intellij.vcs.commit.CommitMode.ExternalCommitMode' enabling.
    }
  }

  private fun getCommitMessage(changeList: LocalChangeList): String {
    if (vcsConfiguration.CLEAR_INITIAL_COMMIT_MESSAGE) return ""
    return getCommitMessageForList(changeList)?.takeIf { it.isNotBlank() }
           ?: getCommitMessageFromVcs(includedChanges())
           ?: vcsConfiguration.LAST_COMMIT_MESSAGE.orEmpty()
  }

  companion object {
    private val LOG = logger<ChangesViewCommitMessagePolicy>()
  }
}