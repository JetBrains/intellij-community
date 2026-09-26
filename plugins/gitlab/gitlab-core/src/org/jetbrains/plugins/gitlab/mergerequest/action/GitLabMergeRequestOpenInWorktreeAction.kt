// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.action

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction

internal class GitLabMergeRequestOpenInWorktreeAction : DumbAwareAction() {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    val vm = e.getData(GitLabMergeRequestsActionKeys.CONNECTED_PROJECT_VM)
    val mergeRequest = e.getData(GitLabMergeRequestsActionKeys.SELECTED)

    e.presentation.isEnabledAndVisible = vm != null && mergeRequest != null && vm.canCheckoutInNewWorktree &&
                                         !vm.isCheckedOut(mergeRequest.iid)
  }

  override fun actionPerformed(e: AnActionEvent) {
    val vm = e.getData(GitLabMergeRequestsActionKeys.CONNECTED_PROJECT_VM) ?: return
    val mergeRequest = e.getData(GitLabMergeRequestsActionKeys.SELECTED) ?: return

    vm.checkoutMergeRequestInNewWorktree(mergeRequest.iid)
  }
}
