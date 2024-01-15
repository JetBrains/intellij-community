// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.action

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import org.jetbrains.plugins.gitlab.mergerequest.ui.details.GitLabMergeRequestViewModel

internal class GitLabMergeRequestRefreshAction : DumbAwareAction() {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

  override fun update(e: AnActionEvent) {
    val ctrl = e.getData(GitLabMergeRequestViewModel.DATA_KEY)
    e.presentation.isEnabled = ctrl != null
  }

  override fun actionPerformed(e: AnActionEvent) {
    val ctrl = e.getRequiredData(GitLabMergeRequestViewModel.DATA_KEY)
    ctrl.refreshData()
  }
}