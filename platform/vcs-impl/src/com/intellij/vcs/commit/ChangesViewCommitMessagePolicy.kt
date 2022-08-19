// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.commit

import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.intellij.openapi.vcs.changes.LocalChangeList

internal class ChangesViewCommitMessagePolicy(project: Project) : AbstractCommitMessagePolicy(project) {
  fun getCommitMessage(changeList: LocalChangeList, changesSupplier: () -> List<Change>): String? =
    if (vcsConfiguration.CLEAR_INITIAL_COMMIT_MESSAGE) null
    else getCommitMessageFor(changeList)?.takeIf { it.isNotBlank() }
         ?: getCommitMessageFromVcs(changesSupplier())
         ?: vcsConfiguration.LAST_COMMIT_MESSAGE

  fun save(changeList: LocalChangeList?, commitMessage: String, saveToHistory: Boolean) {
    if (saveToHistory) vcsConfiguration.saveCommitMessage(commitMessage)
    if (!ChangeListManager.getInstance(project).areChangeListsEnabled()) return // Disposal of ChangesViewCommitWorkflowHandler
    changeList?.let { save(it.name, commitMessage) }
  }
}