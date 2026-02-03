// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.action

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import org.jetbrains.plugins.gitlab.mergerequest.ui.details.GitLabMergeRequestViewModel

abstract class GitLabMergeRequestURLAction : DumbAwareAction() {
  final override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  final override fun update(e: AnActionEvent) {
    val selection = e.getData(GitLabMergeRequestsActionKeys.SELECTED)
    val detailsVm = e.getData(GitLabMergeRequestViewModel.DATA_KEY)

    e.presentation.isEnabledAndVisible = selection != null || detailsVm != null
  }

  final override fun actionPerformed(e: AnActionEvent) {
    val selection = e.getData(GitLabMergeRequestsActionKeys.SELECTED)
    val detailsVm = e.getData(GitLabMergeRequestViewModel.DATA_KEY)
    val url = selection?.webUrl ?: detailsVm?.url ?: return
    handleURL(url)
  }

  abstract fun handleURL(mergeRequestUrl: String)
}