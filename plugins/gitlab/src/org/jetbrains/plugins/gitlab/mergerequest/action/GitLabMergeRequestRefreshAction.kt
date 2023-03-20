// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.action

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabMergeRequest
import org.jetbrains.plugins.gitlab.util.GitLabBundle

internal class GitLabMergeRequestRefreshAction : DumbAwareAction(GitLabBundle.message("merge.request.refresh")) {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

  override fun update(e: AnActionEvent) {
    val mergeRequest: GitLabMergeRequest? = e.getData(GitLabMergeRequestsActionKeys.MERGE_REQUEST)
    e.presentation.isEnabled = mergeRequest != null
  }

  override fun actionPerformed(e: AnActionEvent) {
    val mergeRequest: GitLabMergeRequest = e.getRequiredData(GitLabMergeRequestsActionKeys.MERGE_REQUEST)
    mergeRequest.refreshData()
  }
}