// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui.toolwindow

import com.intellij.collaboration.ui.toolwindow.ReviewToolwindowDataKeys
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import org.jetbrains.plugins.gitlab.mergerequest.ui.toolwindow.model.GitLabToolWindowViewModel
import org.jetbrains.plugins.gitlab.util.GitLabBundle

class GitLabSwitchProjectAndAccountAction : DumbAwareAction(GitLabBundle.message("merge.request.toolwindow.switch.project.account.action.title")) {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabledAndVisible = isEnabledAndVisible(e)
  }

  private fun isEnabledAndVisible(e: AnActionEvent): Boolean {
    val toolwindowVm = e.getData(ReviewToolwindowDataKeys.REVIEW_TOOLWINDOW_VM) as? GitLabToolWindowViewModel ?: return false
    return toolwindowVm.canSwitchProject.value
  }

  override fun actionPerformed(e: AnActionEvent) {
    val toolwindowVm = e.getData(ReviewToolwindowDataKeys.REVIEW_TOOLWINDOW_VM) as?
      GitLabToolWindowViewModel ?: return

    toolwindowVm.switchProject()
  }
}