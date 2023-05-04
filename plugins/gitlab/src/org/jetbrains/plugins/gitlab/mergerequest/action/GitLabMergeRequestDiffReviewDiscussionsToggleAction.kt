// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.action

import com.intellij.collaboration.messages.CollaborationToolsBundle
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ToggleAction
import org.jetbrains.plugins.gitlab.mergerequest.diff.GitLabMergeRequestDiffChangeViewModel
import org.jetbrains.plugins.gitlab.mergerequest.diff.GitLabMergeRequestDiffReviewViewModel

internal class GitLabMergeRequestDiffReviewDiscussionsToggleAction : ToggleAction(
  { CollaborationToolsBundle.message("review.diff.toolbar.show.comments.action") },
  { CollaborationToolsBundle.message("review.diff.toolbar.show.comments.action.description") },
  null
) {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    super.update(e)
    val vm: GitLabMergeRequestDiffChangeViewModel? = e.getData(GitLabMergeRequestDiffReviewViewModel.DATA_KEY)
    e.presentation.isEnabledAndVisible = vm != null
  }

  override fun isSelected(e: AnActionEvent): Boolean {
    val vm: GitLabMergeRequestDiffChangeViewModel = e.getRequiredData(GitLabMergeRequestDiffReviewViewModel.DATA_KEY)
    return vm.isDiscussionsVisible.value
  }

  override fun setSelected(e: AnActionEvent, state: Boolean) {
    val vm: GitLabMergeRequestDiffChangeViewModel = e.getRequiredData(GitLabMergeRequestDiffReviewViewModel.DATA_KEY)
    vm.setDiscussionsVisible(state)
  }
}