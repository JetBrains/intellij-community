// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.plugins.gitlab.mergerequest.action

import com.intellij.collaboration.messages.CollaborationToolsBundle
import com.intellij.collaboration.ui.codereview.action.CodeReviewCheckoutRemoteBranchAction
import com.intellij.collaboration.ui.codereview.details.model.CodeReviewBranchesViewModel
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent

class GitLabMergeRequestCheckoutRemoteBranchAction : CodeReviewCheckoutRemoteBranchAction() {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

  override fun update(e: AnActionEvent) {
    val branchVm: CodeReviewBranchesViewModel? = e.getData(GitLabMergeRequestsActionKeys.REVIEW_BRANCH_VM)

    e.presentation.text = CollaborationToolsBundle.message("review.details.branch.checkout.remote", branchVm?.sourceBranch?.value)
    e.presentation.isEnabled = branchVm != null
  }

  override fun actionPerformed(e: AnActionEvent) {
    val branchVm: CodeReviewBranchesViewModel = e.getRequiredData(GitLabMergeRequestsActionKeys.REVIEW_BRANCH_VM)
    branchVm.fetchAndCheckoutRemoteBranch()
  }
}