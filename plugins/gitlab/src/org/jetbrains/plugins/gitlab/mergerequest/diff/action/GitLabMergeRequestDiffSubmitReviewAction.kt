// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.diff.action

import com.intellij.collaboration.messages.CollaborationToolsBundle
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys
import com.intellij.openapi.actionSystem.ex.CustomComponentAction
import com.intellij.util.ui.JButtonAction
import org.jetbrains.plugins.gitlab.mergerequest.diff.GitLabMergeRequestDiffReviewViewModel
import org.jetbrains.plugins.gitlab.mergerequest.ui.GitLabMergeRequestSubmitReviewPopup
import javax.swing.JComponent

internal class GitLabMergeRequestDiffSubmitReviewAction
  : JButtonAction(CollaborationToolsBundle.message("review.start.submit.action")) {

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    val vm = e.getData(GitLabMergeRequestDiffReviewViewModel.DATA_KEY)
    val review = vm?.submittableReview?.value
    e.presentation.isEnabledAndVisible = vm != null && review != null

    val draftCommentsCount = review?.draftComments ?: 0
    e.presentation.text = if (draftCommentsCount <= 0) {
      CollaborationToolsBundle.message("review.start.submit.action")
    }
    else {
      CollaborationToolsBundle.message("review.start.submit.action.with.comments", draftCommentsCount)
    }
  }

  override fun actionPerformed(e: AnActionEvent) {
    val vm = e.getRequiredData(GitLabMergeRequestDiffReviewViewModel.DATA_KEY)
    val component = e.presentation.getClientProperty(CustomComponentAction.COMPONENT_KEY) as JComponent
    // looks fishy but spares us the need to pass component to VM
    vm.submitReviewInputHandler = {
      GitLabMergeRequestSubmitReviewPopup.show(it, component)
    }
    vm.submitReview()
  }
}