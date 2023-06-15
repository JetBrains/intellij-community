// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.action

import com.intellij.ide.BrowserUtil
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import org.jetbrains.plugins.gitlab.mergerequest.ui.details.model.GitLabMergeRequestDetailsViewModel

class GitLabMergeRequestOpenURLAction : DumbAwareAction() {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    val selection = e.getData(GitLabMergeRequestsActionKeys.SELECTED)
    val detailsVm = e.getData(GitLabMergeRequestDetailsViewModel.DATA_KEY)

    e.presentation.isEnabledAndVisible = selection != null || detailsVm != null
  }

  override fun actionPerformed(e: AnActionEvent) {
    val selection = e.getData(GitLabMergeRequestsActionKeys.SELECTED)
    val detailsVm = e.getData(GitLabMergeRequestDetailsViewModel.DATA_KEY)
    val url = selection?.webUrl ?: detailsVm?.url ?: return
    BrowserUtil.browse(url)
  }
}