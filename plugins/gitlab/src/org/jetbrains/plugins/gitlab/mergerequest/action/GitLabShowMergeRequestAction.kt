// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.action

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabMergeRequestId
import org.jetbrains.plugins.gitlab.mergerequest.file.GitLabTimelinesController
import org.jetbrains.plugins.gitlab.mergerequest.ui.GitLabReviewTabsController
import org.jetbrains.plugins.gitlab.util.GitLabBundle

class GitLabShowMergeRequestAction : DumbAwareAction(GitLabBundle.messagePointer("merge.request.show.action"),
                                                     GitLabBundle.messagePointer("merge.request.show.action.description"),
                                                     null) {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    val project = e.project
    val mergeRequestController: GitLabReviewTabsController? = e.getData(GitLabMergeRequestsActionKeys.REVIEW_TABS_CONTROLLER)
    val selection: GitLabMergeRequestId? = e.getData(GitLabMergeRequestsActionKeys.SELECTED)
    val currentRepository = mergeRequestController?.currentRepository

    e.presentation.isEnabled = project != null && mergeRequestController != null && currentRepository != null && selection != null
  }

  override fun actionPerformed(e: AnActionEvent) {
    val mergeRequestController: GitLabReviewTabsController = e.getRequiredData(GitLabMergeRequestsActionKeys.REVIEW_TABS_CONTROLLER)
    val selection: GitLabMergeRequestId = e.getRequiredData(GitLabMergeRequestsActionKeys.SELECTED)

    mergeRequestController.openReviewDetails(selection)
    GitLabTimelinesController.openTimeline(e.project!!, mergeRequestController.currentRepository!!, selection, false)
  }
}