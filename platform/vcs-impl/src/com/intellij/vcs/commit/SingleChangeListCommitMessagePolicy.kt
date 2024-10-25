// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.commit

import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.intellij.openapi.vcs.changes.LocalChangeList

internal class SingleChangeListCommitMessagePolicy(
  project: Project,
  private val ui: SingleChangeListCommitWorkflowUi,
  private val initialCommitMessage: String?,
  initialChangeList: LocalChangeList,
) : ChangeListCommitMessagePolicy(project, ui.commitMessageUi, initialChangeList, false) {
  override fun getInitialMessage(): String? {
    if (clearInitialCommitMessage || initialCommitMessage != null) return initialCommitMessage

    val commitMessage = getCommitMessageForCurrentList()?.takeIf { it.isNotBlank() }
    return commitMessage ?: vcsConfiguration.LAST_COMMIT_MESSAGE
  }

  override fun getMessageForNewChangeList(): String = getCommitMessageForCurrentList().orEmpty()

  override fun onAfterCommit() {
    val isChangeListFullyIncluded = currentChangeList.changes.size == ui.getIncludedChanges().size
    val isDefaultNameChangeList = currentChangeList.hasDefaultName()
    if (isDefaultNameChangeList && isChangeListFullyIncluded) {
      ChangeListManager.getInstance(project).editComment(currentChangeList.name, "")
    }
  }

  override fun dispose() {
    editCurrentChangeListComment(commitMessageUi.text)
  }
}