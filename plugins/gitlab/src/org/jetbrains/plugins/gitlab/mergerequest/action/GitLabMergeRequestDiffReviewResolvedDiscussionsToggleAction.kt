// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.action

import com.intellij.collaboration.messages.CollaborationToolsBundle
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ToggleAction
import org.jetbrains.plugins.gitlab.mergerequest.diff.GitLabMergeRequestDiffChangeViewModel
import org.jetbrains.plugins.gitlab.mergerequest.diff.GitLabMergeRequestDiffReviewViewModel

internal class GitLabMergeRequestDiffReviewResolvedDiscussionsToggleAction : ToggleAction(
  { CollaborationToolsBundle.message("review.diff.toolbar.show.resolved.threads") },
  { CollaborationToolsBundle.message("review.diff.toolbar.show.resolved.threads.description") },
  null
) {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    super.update(e)
    val vm: GitLabMergeRequestDiffChangeViewModel? = e.getData(GitLabMergeRequestDiffReviewViewModel.DATA_KEY)
    e.presentation.isVisible = vm != null
    e.presentation.isEnabled = vm != null && vm.isDiscussionsVisible.value
  }

  override fun isSelected(e: AnActionEvent): Boolean {
    val vm: GitLabMergeRequestDiffChangeViewModel = e.getRequiredData(GitLabMergeRequestDiffReviewViewModel.DATA_KEY)
    return vm.isResolvedDiscussionsVisible.value
  }

  override fun setSelected(e: AnActionEvent, state: Boolean) {
    val vm: GitLabMergeRequestDiffChangeViewModel = e.getRequiredData(GitLabMergeRequestDiffReviewViewModel.DATA_KEY)
    vm.setResolvedDiscussionsVisible(state)
  }
}