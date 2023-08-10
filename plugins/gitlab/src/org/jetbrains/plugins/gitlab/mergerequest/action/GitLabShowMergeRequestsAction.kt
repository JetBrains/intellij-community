// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.action

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.wm.ToolWindowManager
import org.jetbrains.plugins.gitlab.mergerequest.ui.toolwindow.GitLabToolWindowFactory
import org.jetbrains.plugins.gitlab.util.GitLabBundle

class GitLabShowMergeRequestsAction : DumbAwareAction() {

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

  override fun update(e: AnActionEvent) {
    val project = e.project ?: run {
      e.presentation.isEnabledAndVisible = false
      e.presentation.description = null
      return
    }
    val isAvailable = ToolWindowManager.getInstance(project)
                        .getToolWindow(GitLabToolWindowFactory.ID)?.isAvailable ?: false

    e.presentation.isEnabledAndVisible = isAvailable
    e.presentation.description = if (isAvailable) {
      GitLabBundle.message("action.GitLab.Merge.Request.Show.List.description.enabled")
    }
    else {
      GitLabBundle.message("action.GitLab.Merge.Request.Show.List.description.disabled")
    }
  }

  override fun actionPerformed(e: AnActionEvent) {
    val tw = ToolWindowManager.getInstance(e.project!!).getToolWindow(GitLabToolWindowFactory.ID) ?: return
    tw.activate {}
  }
}