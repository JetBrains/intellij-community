// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui.details

import com.intellij.collaboration.messages.CollaborationToolsBundle.message
import com.intellij.collaboration.ui.codereview.changes.CodeReviewChangeListComponentFactory.SELECTED_CHANGES
import com.intellij.collaboration.ui.codereview.details.model.CodeReviewChangeListViewModel
import com.intellij.collaboration.ui.codereview.details.model.isViewedStateForAllChanges
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.util.NlsActions
import com.intellij.util.asSafely
import org.jetbrains.plugins.gitlab.mergerequest.ui.details.model.GitLabMergeRequestChangeListViewModel

internal abstract class GitLabViewedStateAction(
  dynamicText: @NlsActions.ActionText String,
  private val isViewed: Boolean
) : DumbAwareAction(dynamicText) {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabledAndVisible = false

    val changeListVm = e.getData(CodeReviewChangeListViewModel.DATA_KEY)
                         .asSafely<GitLabMergeRequestChangeListViewModel>() ?: return

    if (!changeListVm.isOnLatest) return

    val selectedChanges = e.getData(SELECTED_CHANGES) ?: return
    val allFilesViewed = changeListVm.isViewedStateForAllChanges(selectedChanges, isViewed)

    e.presentation.isEnabledAndVisible = !allFilesViewed
  }

  override fun actionPerformed(e: AnActionEvent) {
    val changeListVm = e.getData(CodeReviewChangeListViewModel.DATA_KEY) as?
                         GitLabMergeRequestChangeListViewModel ?: return

    val selectedChanges = e.getData(SELECTED_CHANGES) ?: return

    changeListVm.setViewedState(selectedChanges, isViewed)
  }
}

internal class GitLabMarkFilesViewedAction :
  GitLabViewedStateAction(message("action.CodeReview.MarkChangesViewed.text"), true)

internal class GitLabMarkFilesNotViewedAction :
  GitLabViewedStateAction(message("action.CodeReview.MarkChangesNotViewed.text"), false)