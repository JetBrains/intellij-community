// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.commit

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.VcsConfiguration
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.intellij.openapi.vcs.changes.LocalChangeList
import com.intellij.openapi.vcs.changes.ui.CommitMessageProvider

abstract class AbstractCommitMessagePolicy(
  protected val project: Project,
  protected val commitMessageUi: CommitMessageUi,
  private val initDelayedProviders: Boolean
): Disposable {
  protected val vcsConfiguration: VcsConfiguration get() = VcsConfiguration.getInstance(project)
  protected val clearInitialCommitMessage: Boolean get() = vcsConfiguration.CLEAR_INITIAL_COMMIT_MESSAGE

  fun init() {
    if (initDelayedProviders) {
      listenForDelayedProviders(commitMessageUi)
    }

    commitMessageUi.text = getInitialMessage().orEmpty()
  }

  abstract fun getInitialMessage(): String?

  abstract fun onBeforeCommit()

  abstract fun onAfterCommit()

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

  fun onChangelistChanged(newChangeList: LocalChangeList) {
    val oldChangeList = currentChangeList
    currentChangeList = newChangeList
    if (oldChangeList.id != newChangeList.id) {
      onChangelistChanged(oldChangeList, newChangeList)
    }
  }

  protected fun getCommitMessageForCurrentList(): String? {
    val providerMessage = getCommitMessageFromProvider(currentChangeList)
    if (providerMessage != null) return providerMessage

    val changeListDescription = currentChangeList.comment
    if (!changeListDescription.isNullOrBlank()) return changeListDescription

    return if (!currentChangeList.hasDefaultName()) currentChangeList.name else null
  }

  protected abstract fun onChangelistChanged(oldChangeList: LocalChangeList, newChangeList: LocalChangeList)
}