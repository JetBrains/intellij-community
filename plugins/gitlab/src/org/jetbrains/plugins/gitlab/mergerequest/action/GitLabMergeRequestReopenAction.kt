// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.action

import com.intellij.collaboration.async.combineAndCollect
import com.intellij.collaboration.ui.codereview.details.RequestState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.plugins.gitlab.mergerequest.ui.details.model.GitLabMergeRequestReviewFlowViewModel
import org.jetbrains.plugins.gitlab.util.GitLabBundle
import java.awt.event.ActionEvent
import javax.swing.AbstractAction

internal class GitLabMergeRequestReopenAction(
  scope: CoroutineScope,
  private val reviewFlowVm: GitLabMergeRequestReviewFlowViewModel
) : AbstractAction(GitLabBundle.message("merge.request.details.action.review.reopen.text")) {
  init {
    scope.launch {
      combineAndCollect(
        reviewFlowVm.isBusy,
        reviewFlowVm.requestState,
        reviewFlowVm.userCanManageReview
      ) { isBusy, requestState, userCanManageReview ->
        isEnabled = !isBusy && requestState == RequestState.CLOSED && userCanManageReview
      }
    }
  }

  override fun actionPerformed(e: ActionEvent?) = reviewFlowVm.reopen()
}