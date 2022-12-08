// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.commit

import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.intellij.openapi.vcs.changes.LocalChangeList

internal class SingleChangeListCommitMessagePolicy(project: Project, private val initialCommitMessage: String?) :
  AbstractCommitMessagePolicy(project) {

  var defaultNameChangeListMessage: String? = null
  private val messagesToSave = mutableMapOf<String, String>()

  var commitMessage: String? = null
    private set

  fun init(changeList: LocalChangeList, includedChanges: List<Change>) {
    commitMessage = initialCommitMessage
    if (vcsConfiguration.CLEAR_INITIAL_COMMIT_MESSAGE) return

    if (commitMessage != null) {
      defaultNameChangeListMessage = commitMessage
    }
    else {
      commitMessage = getCommitMessageFor(changeList)
      if (commitMessage.isNullOrBlank()) {
        defaultNameChangeListMessage = vcsConfiguration.LAST_COMMIT_MESSAGE
        commitMessage = getCommitMessageFromVcs(includedChanges) ?: defaultNameChangeListMessage
      }
    }
  }

  fun onChangelistChanged(oldChangeList: LocalChangeList, newChangeList: LocalChangeList, currentMessage: String) {
    commitMessage = currentMessage
    if (vcsConfiguration.CLEAR_INITIAL_COMMIT_MESSAGE) return

    if (oldChangeList.name != newChangeList.name) {
      rememberMessage(oldChangeList.name, currentMessage)

      commitMessage = getCommitMessageFor(newChangeList) ?: defaultNameChangeListMessage
    }
  }

  fun save(commitState: ChangeListCommitState, success: Boolean) {
    val changeList = commitState.changeList
    rememberMessage(changeList.name, commitState.commitMessage)

    if (success) {
      vcsConfiguration.saveCommitMessage(commitState.commitMessage)

      val isChangeListFullyIncluded = changeList.changes.size == commitState.changes.size
      if (!isChangeListFullyIncluded) forgetMessage(changeList.name)
    }

    saveMessages()
  }

  fun onAfterCommit(commitState: ChangeListCommitState) {
    val changeList = commitState.changeList
    val isChangeListFullyIncluded = changeList.changes.size == commitState.changes.size
    val isDefaultNameChangeList = changeList.hasDefaultName()
    if (isDefaultNameChangeList && isChangeListFullyIncluded) {
      ChangeListManager.getInstance(project).editComment(changeList.name, "")
    }
  }

  private fun rememberMessage(listName: String, message: String) {
    messagesToSave[listName] = message
  }

  private fun forgetMessage(listName: String) {
    messagesToSave.remove(listName)
  }

  private fun saveMessages() {
    for ((changeListName, commitMessage) in messagesToSave) {
      changeListManager.editComment(changeListName, commitMessage)
    }
  }
}