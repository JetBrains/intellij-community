// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.commit

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.VcsConfiguration
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.intellij.openapi.vcs.changes.LocalChangeList
import com.intellij.openapi.vcs.changes.ui.CommitMessageProvider

/**
 * @see [CommitMessageProvider]
 * @see [DelayedCommitMessageProvider]
 */
abstract class AbstractCommitMessagePolicy(
  protected val project: Project,
  protected val commitMessageUi: CommitMessageUi,
  private val initDelayedProviders: Boolean
): Disposable {
  protected val vcsConfiguration: VcsConfiguration get() = VcsConfiguration.getInstance(project)

  /**
   * Indicates whether the commit message field should be reset after the successful commit
   *
   * @see [onAfterCommit]
   */
  protected open val clearMessageAfterCommit: Boolean get() = vcsConfiguration.CLEAR_INITIAL_COMMIT_MESSAGE

  fun init() {
    if (initDelayedProviders) {
      listenForDelayedProviders(commitMessageUi)
    }

    commitMessageUi.text = getInitialMessage().orEmpty()
  }

  abstract fun getInitialMessage(): String?

  /**
   * Called before execution of the commit session.
   * Used for persisting of the commit message to the commits messages history (see [VcsConfiguration.getRecentMessages]).
   *
   * Extensible via [onBeforeCommit]
   */
  fun onBeforeCommit() {
    val currentMessage = commitMessageUi.text
    vcsConfiguration.saveCommitMessage(currentMessage)

    onBeforeCommit(currentMessage)
  }

  protected open fun onBeforeCommit(currentMessage: String) {}

  fun onAfterCommit() {
    if (clearMessageAfterCommit) {
      commitMessageUi.text = ""
      cleanupStoredMessage()
    }
  }

  /**
   * Called if the commit message should be removed from the storage
   */
  protected abstract fun cleanupStoredMessage()

  protected fun getCommitMessageFromProvider(changeList: LocalChangeList): String? {
    CommitMessageProvider.EXTENSION_POINT_NAME.extensionList.forEach { provider ->
      val providerMessage = provider.getCommitMessage(changeList, project)
      if (providerMessage != null) return providerMessage
    }
    return null
  }

  private fun listenForDelayedProviders(commitMessageUi: CommitMessageUi) {
    CommitMessageProvider.EXTENSION_POINT_NAME.forEachExtensionSafe { extension ->
      if (extension is DelayedCommitMessageProvider) {
        extension.init(project, commitMessageUi, this)
      }
    }
  }
}

abstract class ChangeListCommitMessagePolicy(
  project: Project,
  commitMessageUi: CommitMessageUi,
  initialChangeList: LocalChangeList,
  initDelayedProviders: Boolean,
): AbstractCommitMessagePolicy(project, commitMessageUi, initDelayedProviders) {
  protected val changeListManager: ChangeListManager get() = ChangeListManager.getInstance(project)
  protected var currentChangeList: LocalChangeList = initialChangeList

  /**
   * Called when a new changelist is selected or the current changelist is updated
   */
  fun onChangelistChanged(newChangeList: LocalChangeList) {
    val oldChangeList = currentChangeList
    currentChangeList = newChangeList
    if (oldChangeList.id != newChangeList.id) {
      val commitMessage = commitMessageUi.text
      changeListManager.editComment(oldChangeList.name, commitMessage)
      commitMessageUi.text = getMessageForNewChangeList()
    }
  }

  /**
   * @return new commit message after [currentChangeList] having new [LocalChangeList.getId] was set
   */
  protected abstract fun getMessageForNewChangeList(): String

  override fun onBeforeCommit(currentMessage: String) {
    editCurrentChangeListComment(currentMessage)
  }

  override fun cleanupStoredMessage() {
    editCurrentChangeListComment("")
  }

  protected fun editCurrentChangeListComment(newComment: String) {
    changeListManager.editComment(currentChangeList.name, newComment)
  }

  protected fun getCommitMessageForCurrentList(): String? {
    val providerMessage = getCommitMessageFromProvider(currentChangeList)
    if (providerMessage != null) return providerMessage

    val changeListDescription = currentChangeList.comment
    if (!changeListDescription.isNullOrBlank()) return changeListDescription

    return if (!currentChangeList.hasDefaultName()) currentChangeList.name else null
  }
}