// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.commit

import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.AbstractVcs
import com.intellij.openapi.vcs.VcsConfiguration
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.intellij.openapi.vcs.changes.ChangesUtil
import com.intellij.openapi.vcs.changes.ChangesUtil.processChangesByVcs
import com.intellij.openapi.vcs.changes.LocalChangeList
import com.intellij.openapi.vcs.changes.ui.CommitMessageProvider

abstract class AbstractCommitMessagePolicy(protected val project: Project) {
  protected val vcsConfiguration: VcsConfiguration get() = VcsConfiguration.getInstance(project)
  protected val changeListManager: ChangeListManager get() = ChangeListManager.getInstance(project)

  protected fun save(changeListName: String, commitMessage: String) {
    changeListManager.editComment(changeListName, commitMessage)
  }

  protected fun getCommitMessageFor(changeList: LocalChangeList): String? {
    CommitMessageProvider.EXTENSION_POINT_NAME.extensionList.forEach { provider ->
      val providerMessage = provider.getCommitMessage(changeList, project)
      if (providerMessage != null) return providerMessage
    }

    val changeListDescription = changeList.comment
    if (!changeListDescription.isNullOrBlank()) return changeListDescription

    return if (!changeList.hasDefaultName()) changeList.name else null
  }

  protected fun getCommitMessageFromVcs(changes: List<Change>): String? {
    var result: String? = null
    processChangesByVcs(project, changes) { vcs, vcsChanges ->
      if (result == null) result = getCommitMessageFromVcs(vcs, vcsChanges)
    }
    return result
  }

  private fun getCommitMessageFromVcs(vcs: AbstractVcs, changes: List<Change>): String? =
    vcs.checkinEnvironment?.getDefaultMessageFor(ChangesUtil.getPaths(changes).toTypedArray())
}