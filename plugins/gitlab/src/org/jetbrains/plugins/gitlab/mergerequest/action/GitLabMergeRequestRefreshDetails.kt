// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.action

import com.intellij.collaboration.messages.CollaborationToolsBundle
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import org.jetbrains.plugins.gitlab.mergerequest.ui.details.model.GitLabMergeRequestDetailsLoadingViewModel

internal class GitLabMergeRequestRefreshDetails : DumbAwareAction(CollaborationToolsBundle.message("review.details.action.refresh")) {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

  override fun update(e: AnActionEvent) {
    val detailsLoadingVm: GitLabMergeRequestDetailsLoadingViewModel? = e.getData(GitLabMergeRequestsActionKeys.REVIEW_DETAILS_LOADING_VM)
    e.presentation.isEnabled = detailsLoadingVm != null
  }

  override fun actionPerformed(e: AnActionEvent) {
    e.getRequiredData(GitLabMergeRequestsActionKeys.REVIEW_DETAILS_LOADING_VM).requestLoad()
  }
}