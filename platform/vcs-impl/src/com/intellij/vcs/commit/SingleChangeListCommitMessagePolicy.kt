// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.commit

import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.intellij.openapi.vcs.changes.LocalChangeList
import com.intellij.ui.TextAccessor

internal class SingleChangeListCommitMessagePolicy(project: Project, private val initialCommitMessage: String?) :
  AbstractCommitMessagePolicy(project) {

  private var lastKnownComment: String? = null
  private val messagesToSave = mutableMapOf<String, String>()

  private var commitMessage: String? = null

  fun init(changeList: LocalChangeList, includedChanges: List<Change>): String? {
    commitMessage = initialCommitMessage
    if (vcsConfiguration.CLEAR_INITIAL_COMMIT_MESSAGE) return commitMessage

    if (commitMessage != null) {
      lastKnownComment = commitMessage
    }
    else {
      commitMessage = getCommitMessageFor(changeList)
      if (commitMessage.isNullOrBlank()) {
        lastKnownComment = vcsConfiguration.LAST_COMMIT_MESSAGE
        commitMessage = getCommitMessageFromVcs(includedChanges) ?: lastKnownComment
      }
    }
    return commitMessage
  }

  /**
   * Ex: via "Commit Message History" action
   */
  fun onCommitMessageReset(text: String?) {
    lastKnownComment = text
  }

  fun onChangelistChanged(oldChangeList: LocalChangeList, newChangeList: LocalChangeList, currentMessage: TextAccessor) {
    commitMessage = currentMessage.text
    if (vcsConfiguration.CLEAR_INITIAL_COMMIT_MESSAGE) return

    if (oldChangeList.name != newChangeList.name) {
      messagesToSave[oldChangeList.name] = currentMessage.text

      commitMessage = getCommitMessageFor(newChangeList) ?: lastKnownComment
      currentMessage.text = commitMessage
    }
  }

  fun onDialogClosed(commitState: ChangeListCommitState, onCommit: Boolean) {
    val changeList = commitState.changeList
    val currentMessage = commitState.commitMessage

    messagesToSave[changeList.name] = currentMessage

    if (onCommit) {
      vcsConfiguration.saveCommitMessage(currentMessage)

      val isChangeListFullyIncluded = changeList.changes.size == commitState.changes.size
      if (!isChangeListFullyIncluded) {
        // keep original changelist description
        messagesToSave.remove(changeList.name)
      }
    }

    for ((changeListName, description) in messagesToSave) {
      changeListManager.editComment(changeListName, description)
    }
  }

  fun onAfterCommit(commitState: ChangeListCommitState) {
    val changeList = commitState.changeList
    val isChangeListFullyIncluded = changeList.changes.size == commitState.changes.size
    val isDefaultNameChangeList = changeList.hasDefaultName()
    if (isDefaultNameChangeList && isChangeListFullyIncluded) {
      ChangeListManager.getInstance(project).editComment(changeList.name, "")
    }
  }
}