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
@ApiStatus.Internal
abstract class AbstractCommitMessagePolicy(
  protected val project: Project,
  protected val commitMessageUi: CommitMessageUi,
) : Disposable {
  protected val vcsConfiguration: VcsConfiguration get() = VcsConfiguration.getInstance(project)

  private var lastSetMessage: CommitMessage? = null // last message that was not a user input

  fun init() {
    listenForDelayedProviders(commitMessageUi, this)
    setCommitMessage(getNewCommitMessage() ?: CommitMessage.EMPTY)
  }

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

  open fun onAfterCommit() {
    if (clearMessageAfterCommit) {
      setCommitMessage(CommitMessage.EMPTY)
      cleanupStoredMessage()
    }
    else {
      setCommitMessage(getNewCommitMessage() ?: CommitMessage.EMPTY)
    }
  }

  abstract override fun dispose()

  /**
   * Indicates whether the commit message field should be reset after the successful commit
   *
   * @see [onAfterCommit]
   */
  protected open val clearMessageAfterCommit: Boolean get() = vcsConfiguration.CLEAR_INITIAL_COMMIT_MESSAGE

  protected fun setCommitMessage(message: CommitMessage) {
    lastSetMessage = message
    commitMessageUi.text = message.text
  }

  protected val currentMessageIsDisposable: Boolean
    get() {
      val lastSetMessage = lastSetMessage ?: return false
      return lastSetMessage.disposable == true && lastSetMessage.text.trim() == commitMessageUi.text.trim()
    }


  protected abstract val delayedMessagesProvidersSupport: DelayedMessageProvidersSupport?

  protected abstract fun getNewCommitMessage(): CommitMessage?

  /**
   * Called if the commit message should be removed from the storage
   */
  protected abstract fun cleanupStoredMessage()

  companion object {
    private fun listenForDelayedProviders(
      commitMessageUi: CommitMessageUi,
      messagePolicy: AbstractCommitMessagePolicy,
    ) {
      val delayedMessageSupport = messagePolicy.delayedMessagesProvidersSupport ?: return

      val controller = DelayedMessageController(messagePolicy, delayedMessageSupport)
      val project = messagePolicy.project

      DefaultCommitMessagePolicy.EXTENSION_POINT_NAME.forEachExtensionSafe { extension ->
        if (extension.enabled(project)) {
          extension.initAsyncMessageUpdate(project, controller, messagePolicy)
        }
      }

      CommitMessageProvider.EXTENSION_POINT_NAME.forEachExtensionSafe { extension ->
        if (extension is DelayedCommitMessageProvider) {
          extension.init(project, commitMessageUi, messagePolicy)
        }
      }
    }

    fun getCommitMessageFromProvider(project: Project, changeList: LocalChangeList): CommitMessage? {
      DefaultCommitMessagePolicy.EXTENSION_POINT_NAME.extensionList.forEach { provider ->
        if (provider.enabled(project)) {
          val message = provider.getMessage(project)
          if (message != null) return message
        }
      }

      CommitMessageProvider.EXTENSION_POINT_NAME.extensionList.forEach { provider ->
        val legacyProviderMessage = provider.getCommitMessage(changeList, project)
        if (legacyProviderMessage != null) return CommitMessage(legacyProviderMessage)
      }

      return null
    }
  }

  private class DelayedMessageController(
    val messagePolicy: AbstractCommitMessagePolicy,
    val delayedMessageSupport: DelayedMessageProvidersSupport,
  ) : DefaultCommitMessagePolicy.CommitMessageController {
    override fun setCommitMessage(message: CommitMessage) {
      if (!messagePolicy.currentMessageIsDisposable) {
        delayedMessageSupport.saveCurrentCommitMessage()
      }
      messagePolicy.setCommitMessage(message)
    }

    override fun tryRestoreCommitMessage() {
      if (messagePolicy.currentMessageIsDisposable) {
        messagePolicy.setCommitMessage(delayedMessageSupport.restoredCommitMessage() ?: CommitMessage.EMPTY)
      }
    }
  }

  @ApiStatus.Internal
  interface DelayedMessageProvidersSupport {
    fun saveCurrentCommitMessage()
    fun restoredCommitMessage(): CommitMessage?
  }
}

internal abstract class ChangeListCommitMessagePolicy(
  project: Project,
  commitMessageUi: CommitMessageUi,
  initialChangeList: LocalChangeList,
) : AbstractCommitMessagePolicy(project, commitMessageUi) {
  protected val changeListManager: ChangeListManager get() = ChangeListManager.getInstance(project)

  protected var currentChangeList: LocalChangeList = initialChangeList

  override fun onBeforeCommit() {
    super.onBeforeCommit()
    saveMessageToChangeListDescription()
  }

  override fun dispose() {
    if (changeListManager.areChangeListsEnabled()) {
      saveMessageToChangeListDescription()
    }
    else {
      // Disposal of ChangesViewCommitWorkflowHandler on 'com.intellij.vcs.commit.CommitMode.ExternalCommitMode' enabling.
    }
  }


  override fun cleanupStoredMessage() {
    changeListManager.editComment(currentChangeList.name, "")
  }

  /**
   * Called when a new changelist is selected or the current changelist is updated
   */
  fun onChangelistChanged(newChangeList: LocalChangeList) {
    val oldChangeList = currentChangeList
    currentChangeList = newChangeList
    if (oldChangeList.id != newChangeList.id) {
      changeListManager.editComment(oldChangeList.name, commitMessageUi.text)

      val newMessage = getCommitMessageForCurrentList() ?: CommitMessage.EMPTY
      setCommitMessage(newMessage)
    }
  }


  protected fun saveMessageToChangeListDescription() {
    if (!currentMessageIsDisposable) {
      changeListManager.editComment(currentChangeList.name, commitMessageUi.text)
    }
  }

  protected fun getCommitMessageForCurrentList(): CommitMessage? {
    val providerMessage = getCommitMessageFromProvider(project, currentChangeList)
    return providerMessage
           ?: getCommitMessageFromChangelistDescription()
  }

  protected fun getCommitMessageFromChangelistDescription(): CommitMessage? {
    val changeListDescription = currentChangeList.comment
    if (!changeListDescription.isNullOrBlank()) return CommitMessage(changeListDescription)

    if (currentChangeList.hasDefaultName()) return null
    return CommitMessage(currentChangeList.name)
  }
}

@ApiStatus.Internal
open class CommitMessage(val text: String, val disposable: Boolean = false) {
  companion object {
    val EMPTY: CommitMessage = CommitMessage("")
  }
}