// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.commit

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.VcsConfiguration
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.intellij.openapi.vcs.changes.LocalChangeList
import com.intellij.openapi.vcs.changes.ui.CommitMessageProvider
import org.jetbrains.annotations.ApiStatus

/**
 * @see [CommitMessageProvider]
 * @see [DelayedCommitMessageProvider]
 */
abstract class AbstractCommitMessagePolicy(
  protected val project: Project,
  protected val commitMessageUi: CommitMessageUi,
): Disposable {
  protected abstract val delayedMessagesProvidersSupport: DelayedMessageProvidersSupport?

  protected val vcsConfiguration: VcsConfiguration get() = VcsConfiguration.getInstance(project)

  /**
   * Indicates whether the commit message field should be reset after the successful commit
   *
   * @see [onAfterCommit]
   */
  protected open val clearMessageAfterCommit: Boolean get() = vcsConfiguration.CLEAR_INITIAL_COMMIT_MESSAGE

  protected val currentMessageIsDisposable: Boolean
    get() {
      val lastSetMessage = lastSetMessage
      return lastSetMessage?.disposable == true && lastSetMessage.text.trim() == commitMessageUi.text.trim()
    }

  private var lastSetMessage: CommitMessage? = null

  fun init() {
    delayedMessagesProvidersSupport?.let { listenForDelayedProviders(commitMessageUi, it) }
    setCommitMessage(getInitialMessage() ?: CommitMessage.EMPTY)
  }

  abstract fun getInitialMessage(): CommitMessage?

  /**
   * Called before execution of the commit session.
   * Used for persisting of the commit message to the commits messages history (see [VcsConfiguration.getRecentMessages]).
   */
  open fun onBeforeCommit() {
    if (!currentMessageIsDisposable) {
      val currentMessage = commitMessageUi.text
      vcsConfiguration.saveCommitMessage(currentMessage)
    }
  }

  fun onAfterCommit() {
    if (clearMessageAfterCommit) {
      setCommitMessage(CommitMessage.EMPTY)
      cleanupStoredMessage()
    } else {
      val newMessage = getNewMessageAfterCommit()
      if (newMessage != null) {
        setCommitMessage(newMessage)
      }
    }
  }

  /**
   * Called only if [clearMessageAfterCommit] returned false
   *
   * @return null if commit field should remain as is or new value for the commit message otherwise
   */
  open fun getNewMessageAfterCommit(): CommitMessage? = null

  /**
   * Called if the commit message should be removed from the storage
   */
  protected abstract fun cleanupStoredMessage()

  protected fun getCommitMessageFromProvider(changeList: LocalChangeList?): CommitMessage? {
    DefaultCommitMessagePolicy.EXTENSION_POINT_NAME.extensionList.forEach { provider ->
      if (provider.enabled(project)) {
        val message = provider.getMessage(project)
        if (message != null) return message
      }
    }

    CommitMessageProvider.EXTENSION_POINT_NAME.extensionList.forEach { provider ->
      val changeListOrDefault = changeList
                                ?: ChangeListManager.getInstance(project).defaultChangeList // always blank, required for 'CommitMessageProvider'
      val legacyProviderMessage = provider.getCommitMessage(changeListOrDefault, project)
      if (legacyProviderMessage != null) return CommitMessage(legacyProviderMessage)
    }

    return null
  }

  protected fun setCommitMessage(message: CommitMessage) {
    lastSetMessage = message
    commitMessageUi.text = message.text
  }

  private fun listenForDelayedProviders(commitMessageUi: CommitMessageUi, delayedCommitMessagesProvidersSupport: DelayedMessageProvidersSupport) {
    DefaultCommitMessagePolicy.EXTENSION_POINT_NAME.extensionList.forEach { extension ->
      if (extension.enabled(project)) {
        extension.initAsyncMessageUpdate(project, object : DefaultCommitMessagePolicy.CommitMessageController {
          override fun setCommitMessage(message: CommitMessage) {
            if (!message.disposable) {
              delayedMessagesProvidersSupport?.saveCurrentCommitMessage()
            }
            this@AbstractCommitMessagePolicy.setCommitMessage(message)
          }

          override fun tryRestoreCommitMessage() {
            if (currentMessageIsDisposable) {
              this@AbstractCommitMessagePolicy.setCommitMessage(delayedCommitMessagesProvidersSupport.restoredCommitMessage() ?: CommitMessage.EMPTY)
            }
          }
        }, this)
      }
    }

    CommitMessageProvider.EXTENSION_POINT_NAME.forEachExtensionSafe { extension ->
      if (extension is DelayedCommitMessageProvider) {
        extension.init(project, commitMessageUi, this)
      }
    }
  }

  @ApiStatus.Experimental
  interface DelayedMessageProvidersSupport {
    fun saveCurrentCommitMessage()
    fun restoredCommitMessage(): CommitMessage?
  }
}

abstract class ChangeListCommitMessagePolicy(
  project: Project,
  commitMessageUi: CommitMessageUi,
  initialChangeList: LocalChangeList,
): AbstractCommitMessagePolicy(project, commitMessageUi) {
  protected val changeListManager: ChangeListManager get() = ChangeListManager.getInstance(project)
  protected var currentChangeList: LocalChangeList = initialChangeList

  override fun getInitialMessage(): CommitMessage? = getCommitMessageForCurrentList()

  /**
   * Called when a new changelist is selected or the current changelist is updated
   */
  fun onChangelistChanged(newChangeList: LocalChangeList) {
    val oldChangeList = currentChangeList
    currentChangeList = newChangeList
    if (oldChangeList.id != newChangeList.id) {
      val commitMessage = commitMessageUi.text
      changeListManager.editComment(oldChangeList.name, commitMessage)
      setCommitMessage(getMessageForNewChangeList())
    }
  }

  /**
   * @return new commit message after [currentChangeList] having new [LocalChangeList.getId] was set
   */
  protected open fun getMessageForNewChangeList(): CommitMessage = getCommitMessageForCurrentList() ?: CommitMessage.EMPTY

  override fun onBeforeCommit() {
    super.onBeforeCommit()
    saveMessageToChangeListDescription()
  }

  override fun cleanupStoredMessage() {
    editCurrentChangeListComment("")
  }

  protected fun saveMessageToChangeListDescription() {
    if (!currentMessageIsDisposable) {
      editCurrentChangeListComment(commitMessageUi.text)
    }
  }

  private fun editCurrentChangeListComment(newComment: String) {
    changeListManager.editComment(currentChangeList.name, newComment)
  }

  protected fun getCommitMessageForCurrentList(): CommitMessage? {
    val providerMessage = getCommitMessageFromProvider(currentChangeList)
    if (providerMessage != null) return providerMessage

    val changeListDescription = currentChangeList.comment
    if (!changeListDescription.isNullOrBlank()) return CommitMessage(changeListDescription)

    return if (!currentChangeList.hasDefaultName()) CommitMessage(currentChangeList.name) else null
  }
}

@ApiStatus.Internal
open class CommitMessage(val text: String, val disposable: Boolean = false) {
  @ApiStatus.Internal
  companion object {
    val EMPTY: CommitMessage = CommitMessage("")
  }
}