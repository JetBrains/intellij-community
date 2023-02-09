// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.commit

import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.intellij.openapi.vcs.changes.LocalChangeList
import com.intellij.ui.TextAccessor

internal class SingleChangeListCommitMessagePolicy(project: Project, initialCommitMessage: String?) :
  AbstractCommitMessagePolicy(project) {

  private var lastKnownComment: String? = initialCommitMessage
  private val messagesToSave = mutableMapOf<String, String>()

  fun init(changeList: LocalChangeList, includedChanges: List<Change>): String? {
    if (vcsConfiguration.CLEAR_INITIAL_COMMIT_MESSAGE) return lastKnownComment

    if (lastKnownComment != null) {
      return lastKnownComment
    }

    val commitMessage = getCommitMessageForList(changeList)?.takeIf { it.isNotBlank() }
    if (commitMessage != null) return commitMessage

    lastKnownComment = vcsConfiguration.LAST_COMMIT_MESSAGE
    return getCommitMessageFromVcs(includedChanges) ?: lastKnownComment
  }

  /**
   * Ex: via "Commit Message History" action
   */
  fun onCommitMessageReset(text: String?) {
    lastKnownComment = text
  }

  fun onChangelistChanged(oldChangeList: LocalChangeList, newChangeList: LocalChangeList, currentMessage: TextAccessor) {
    if (vcsConfiguration.CLEAR_INITIAL_COMMIT_MESSAGE) return
    if (oldChangeList.name == newChangeList.name) return

    messagesToSave[oldChangeList.name] = currentMessage.text

    currentMessage.text = getCommitMessageForList(newChangeList) ?: lastKnownComment
  }

  fun onDialogClosed(commitState: ChangeListCommitState, onBeforeCommit: Boolean) {
    val changeList = commitState.changeList
    val currentMessage = commitState.commitMessage

    messagesToSave[changeList.name] = currentMessage

    if (onBeforeCommit) {
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