// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.plugins.github.pullrequest.action

import com.intellij.collaboration.messages.CollaborationToolsBundle
import com.intellij.collaboration.ui.codereview.action.CodeReviewCheckoutRemoteBranchAction
import com.intellij.collaboration.ui.codereview.details.model.CodeReviewBranchesViewModel
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent

class GHPRCheckoutRemoteBranchAction : CodeReviewCheckoutRemoteBranchAction() {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

  override fun update(e: AnActionEvent) {
    val branchesVm: CodeReviewBranchesViewModel? = e.getData(GHPRActionKeys.REVIEW_BRANCH_VM)

    e.presentation.text = CollaborationToolsBundle.message("review.details.branch.checkout.remote", branchesVm?.sourceBranch?.value)
    e.presentation.isEnabled = branchesVm != null
  }

  override fun actionPerformed(e: AnActionEvent) {
    val branchesVm: CodeReviewBranchesViewModel = e.getRequiredData(GHPRActionKeys.REVIEW_BRANCH_VM)
    branchesVm.fetchAndCheckoutRemoteBranch()
  }
}