// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.action

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabMergeRequestId
import org.jetbrains.plugins.gitlab.mergerequest.file.GitLabFilesController
import org.jetbrains.plugins.gitlab.mergerequest.ui.details.GitLabMergeRequestController
import org.jetbrains.plugins.gitlab.util.GitLabBundle

class GitLabShowMergeRequestAction : DumbAwareAction(GitLabBundle.messagePointer("merge.request.show.action"),
                                                     GitLabBundle.messagePointer("merge.request.show.action.description"),
                                                     null) {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    val mergeRequestController: GitLabMergeRequestController? = e.getData(GitLabMergeRequestsActionKeys.MERGE_REQUEST_CONTROLLER)
    val filesController: GitLabFilesController? = e.getData(GitLabMergeRequestsActionKeys.FILES_CONTROLLER)
    val selection: GitLabMergeRequestId? = e.getData(GitLabMergeRequestsActionKeys.SELECTED)

    e.presentation.isEnabled = mergeRequestController != null && filesController != null && selection != null
  }

  override fun actionPerformed(e: AnActionEvent) {
    val mergeRequestController: GitLabMergeRequestController = e.getRequiredData(GitLabMergeRequestsActionKeys.MERGE_REQUEST_CONTROLLER)
    val filesController: GitLabFilesController = e.getRequiredData(GitLabMergeRequestsActionKeys.FILES_CONTROLLER)
    val selection: GitLabMergeRequestId = e.getRequiredData(GitLabMergeRequestsActionKeys.SELECTED)

    mergeRequestController.openMergeRequest(selection)
    filesController.openTimeline(selection, false)
  }
}