// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.action

import com.intellij.collaboration.ui.toolwindow.ReviewToolwindowDataKeys
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabMergeRequestDetails
import org.jetbrains.plugins.gitlab.mergerequest.ui.toolwindow.GitLabReviewTab
import org.jetbrains.plugins.gitlab.mergerequest.ui.toolwindow.model.GitLabToolWindowProjectViewModel
import org.jetbrains.plugins.gitlab.util.GitLabStatistics

private class GitLabShowMergeRequestAction : DumbAwareAction() {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    val projectVm = e.getData(ReviewToolwindowDataKeys.REVIEW_TOOLWINDOW_PROJECT_VM) as? GitLabToolWindowProjectViewModel
    val selection: GitLabMergeRequestDetails? = e.getData(GitLabMergeRequestsActionKeys.SELECTED)

    e.presentation.isEnabledAndVisible = projectVm != null && selection != null
  }

  override fun actionPerformed(e: AnActionEvent) {
    val projectVm = e.getRequiredData(ReviewToolwindowDataKeys.REVIEW_TOOLWINDOW_PROJECT_VM) as GitLabToolWindowProjectViewModel
    val selection: GitLabMergeRequestDetails = e.getRequiredData(GitLabMergeRequestsActionKeys.SELECTED)

    projectVm.showTab(GitLabReviewTab.ReviewSelected(selection.iid), GitLabStatistics.ToolWindowOpenTabActionPlace.ACTION)
    projectVm.filesController.openTimeline(selection.iid, false)
  }
}