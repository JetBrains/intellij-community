// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.AbstractVcs
import com.intellij.openapi.vcs.VcsConfiguration
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.intellij.openapi.vcs.changes.ChangesUtil
import com.intellij.openapi.vcs.changes.ChangesUtil.processChangesByVcs
import com.intellij.openapi.vcs.changes.LocalChangeList

class SingleChangeListCommitMessagePolicy(private val project: Project, private val initialCommitMessage: String?) {
  private val vcsConfiguration = VcsConfiguration.getInstance(project)
  private val changeListManager = ChangeListManager.getInstance(project)

  var defaultNameChangeListMessage: String? = null
  private var lastChangeListName: String? = null
  private val messagesToSave = mutableMapOf<String, String>()

  var commitMessage: String? = null
    private set

  fun init(changeList: LocalChangeList, includedChanges: List<Change>) {
    if (vcsConfiguration.CLEAR_INITIAL_COMMIT_MESSAGE) return

    lastChangeListName = changeList.name

    if (initialCommitMessage != null) {
      defaultNameChangeListMessage = initialCommitMessage
      commitMessage = initialCommitMessage
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

  fun save(changeList: LocalChangeList, includedChanges: List<Change>, currentMessage: String, success: Boolean) {
    rememberMessage(currentMessage)

    if (success) {
      vcsConfiguration.saveCommitMessage(currentMessage)

      val entireChangeListIncluded = changeList.changes.size == includedChanges.size
      if (!entireChangeListIncluded) forgetMessage()
    }

    saveMessages()
  }

  private fun getCommitMessageFor(changeList: LocalChangeList): String? {
    CommitMessageProvider.EXTENSION_POINT_NAME.extensionList.forEach { provider ->
      val providerMessage = provider.getCommitMessage(changeList, project)
      if (providerMessage != null) return providerMessage
    }

    val changeListDescription = changeList.comment
    if (!changeListDescription.isNullOrBlank()) return changeListDescription

    return if (!changeList.hasDefaultName()) changeList.name else null
  }

  private fun getCommitMessageFromVcs(changes: List<Change>): String? {
    var result: String? = null
    processChangesByVcs(project, changes) { vcs, vcsChanges ->
      if (result == null) result = getCommitMessageFromVcs(vcs, vcsChanges)
    }
    return result
  }

  private fun getCommitMessageFromVcs(vcs: AbstractVcs<*>, changes: List<Change>): String? =
    vcs.checkinEnvironment?.getDefaultMessageFor(ChangesUtil.getPaths(changes).toTypedArray())

  private fun rememberMessage(message: String) = lastChangeListName?.let { messagesToSave[it] = message }

  private fun forgetMessage() = lastChangeListName?.let { messagesToSave -= it }

  private fun saveMessages() =
    messagesToSave.forEach { changeListName, commitMessage -> changeListManager.editComment(changeListName, commitMessage) }
}