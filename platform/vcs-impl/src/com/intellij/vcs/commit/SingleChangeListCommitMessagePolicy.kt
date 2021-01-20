// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.commit

import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.LocalChangeList

internal class SingleChangeListCommitMessagePolicy(project: Project, private val initialCommitMessage: String?) :
  AbstractCommitMessagePolicy(project) {

  var defaultNameChangeListMessage: String? = null
  private var lastChangeListName: String? = null
  private val messagesToSave = mutableMapOf<String, String>()

  var commitMessage: String? = null
    private set

  fun init(changeList: LocalChangeList, includedChanges: List<Change>) {
    commitMessage = initialCommitMessage
    if (vcsConfiguration.CLEAR_INITIAL_COMMIT_MESSAGE) return

    lastChangeListName = changeList.name

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

  fun update(changeList: LocalChangeList, currentMessage: String) {
    commitMessage = currentMessage
    if (vcsConfiguration.CLEAR_INITIAL_COMMIT_MESSAGE) return

    if (changeList.name != lastChangeListName) {
      rememberMessage(currentMessage)

      lastChangeListName = changeList.name
      commitMessage = getCommitMessageFor(changeList) ?: defaultNameChangeListMessage
    }
  }

  fun save(commitState: ChangeListCommitState, success: Boolean) {
    rememberMessage(commitState.commitMessage)

    if (success) {
      vcsConfiguration.saveCommitMessage(commitState.commitMessage)

      val entireChangeListIncluded = commitState.changeList.changes.size == commitState.changes.size
      if (!entireChangeListIncluded) forgetMessage()
    }

    saveMessages()
  }

  private fun rememberMessage(message: String) = lastChangeListName?.let { messagesToSave[it] = message }

  private fun forgetMessage() = lastChangeListName?.let { messagesToSave -= it }

  private fun saveMessages() = messagesToSave.forEach { (changeListName, commitMessage) -> save(changeListName, commitMessage) }
}