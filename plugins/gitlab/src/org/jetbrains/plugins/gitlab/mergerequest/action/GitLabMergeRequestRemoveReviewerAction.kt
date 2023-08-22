// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.action

import com.intellij.collaboration.async.combineAndCollect
import com.intellij.collaboration.messages.CollaborationToolsBundle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.plugins.gitlab.api.dto.GitLabUserDTO
import org.jetbrains.plugins.gitlab.mergerequest.ui.details.model.GitLabMergeRequestReviewFlowViewModel
import java.awt.event.ActionEvent
import javax.swing.AbstractAction

internal class GitLabMergeRequestRemoveReviewerAction(
  scope: CoroutineScope,
  private val reviewFlowVm: GitLabMergeRequestReviewFlowViewModel,
  private val reviewer: GitLabUserDTO
) : AbstractAction(CollaborationToolsBundle.message("review.details.action.remove.reviewer", reviewer.name)) {
  init {
    scope.launch {
      combineAndCollect(reviewFlowVm.isBusy, reviewFlowVm.userCanManage) { isBusy, userCanManageReview ->
        isEnabled = !isBusy && userCanManageReview
      }
    }
  }

  override fun actionPerformed(event: ActionEvent) = reviewFlowVm.removeReviewer(reviewer)
}