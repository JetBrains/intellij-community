// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.action

import com.intellij.collaboration.ui.codereview.details.data.ReviewRequestState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import org.jetbrains.plugins.gitlab.mergerequest.ui.details.model.GitLabMergeRequestReviewFlowViewModel
import org.jetbrains.plugins.gitlab.util.GitLabBundle
import java.awt.event.ActionEvent
import javax.swing.AbstractAction

internal class GitLabMergeRequestPostReviewAction(
  scope: CoroutineScope,
  private val reviewFlowVm: GitLabMergeRequestReviewFlowViewModel
) : AbstractAction(GitLabBundle.message("merge.request.details.action.review.post.text")) {
  init {
    scope.launch {
      combine(
        reviewFlowVm.isBusy,
        reviewFlowVm.reviewRequestState,
        reviewFlowVm.userCanManage
      ) { isBusy, reviewRequestState, userCanManageReview ->
        isEnabled = !isBusy && reviewRequestState == ReviewRequestState.DRAFT && userCanManageReview
      }
    }
  }

  override fun actionPerformed(e: ActionEvent?) = reviewFlowVm.postReview()
}