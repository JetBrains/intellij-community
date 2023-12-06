// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.action

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import org.jetbrains.plugins.gitlab.mergerequest.ui.list.GitLabMergeRequestsListViewModel
import org.jetbrains.plugins.gitlab.util.GitLabBundle

internal class GitLabMergeRequestListRefreshAction : DumbAwareAction(GitLabBundle.message("merge.request.list.refresh")) {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

  override fun update(e: AnActionEvent) {
    val reviewListVm: GitLabMergeRequestsListViewModel? = e.getData(GitLabMergeRequestsActionKeys.REVIEW_LIST_VM)
    e.presentation.isEnabled = reviewListVm != null
  }

  override fun actionPerformed(e: AnActionEvent) {
    val reviewListVm: GitLabMergeRequestsListViewModel = e.getRequiredData(GitLabMergeRequestsActionKeys.REVIEW_LIST_VM)
    reviewListVm.refresh()
  }
}