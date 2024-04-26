// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.action

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAwareAction
import org.jetbrains.plugins.gitlab.mergerequest.ui.toolwindow.model.GitLabToolWindowViewModel
import org.jetbrains.plugins.gitlab.util.GitLabBundle

class GitLabShowMergeRequestsAction : DumbAwareAction() {

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

  override fun update(e: AnActionEvent) {
    val project = e.project ?: run {
      e.presentation.isEnabledAndVisible = false
      e.presentation.description = null
      return
    }
    val isAvailable = project.service<GitLabToolWindowViewModel>().isAvailable.value

    e.presentation.isEnabledAndVisible = isAvailable
    e.presentation.description = if (isAvailable) {
      GitLabBundle.message("action.GitLab.Merge.Request.Show.List.description.enabled")
    }
    else {
      GitLabBundle.message("action.GitLab.Merge.Request.Show.List.description.disabled")
    }
  }

  override fun actionPerformed(e: AnActionEvent) {
    e.project!!.service<GitLabToolWindowViewModel>().activateAndAwaitProject {
      selectTab(null)
    }
  }
}