// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui.toolwindow

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import org.jetbrains.plugins.gitlab.mergerequest.action.GitLabMergeRequestsActionKeys
import org.jetbrains.plugins.gitlab.util.GitLabBundle

class GitLabSwitchProjectAndAccountAction : DumbAwareAction(GitLabBundle.message("merge.request.toolwindow.switch.project.account.action.title")) {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabledAndVisible = isEnabledAndVisible(e)
  }

  private fun isEnabledAndVisible(e: AnActionEvent): Boolean {
    val vm = e.getData(GitLabMergeRequestsActionKeys.PROJECT_VM) ?: return false
    return vm.canSwitchProject.value
  }

  override fun actionPerformed(e: AnActionEvent) {
    val vm = e.getData(GitLabMergeRequestsActionKeys.PROJECT_VM) ?: return
    vm.switchProject()
  }
}