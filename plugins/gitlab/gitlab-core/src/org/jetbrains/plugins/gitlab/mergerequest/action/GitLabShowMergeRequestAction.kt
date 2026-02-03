// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.action

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabMergeRequestDetails
import org.jetbrains.plugins.gitlab.util.GitLabStatistics

internal class GitLabShowMergeRequestAction : DumbAwareAction() {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    val projectVm = e.getData(GitLabMergeRequestsActionKeys.CONNECTED_PROJECT_VM)
    val selection: GitLabMergeRequestDetails? = e.getData(GitLabMergeRequestsActionKeys.SELECTED)

    e.presentation.isEnabledAndVisible = projectVm != null && selection != null
  }

  override fun actionPerformed(e: AnActionEvent) {
    val projectVm = e.getData(GitLabMergeRequestsActionKeys.CONNECTED_PROJECT_VM) ?: return
    val selection = e.getData(GitLabMergeRequestsActionKeys.SELECTED) ?: return

    projectVm.openMergeRequestDetails(selection.iid, GitLabStatistics.ToolWindowOpenTabActionPlace.ACTION, false)
    projectVm.openMergeRequestTimeline(selection.iid, false)
  }
}