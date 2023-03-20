// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.action

import com.intellij.collaboration.async.combineAndCollect
import com.intellij.collaboration.messages.CollaborationToolsBundle
import com.intellij.collaboration.ui.codereview.details.RequestState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.plugins.gitlab.mergerequest.ui.details.model.GitLabMergeRequestReviewFlowViewModel
import java.awt.event.ActionEvent
import javax.swing.AbstractAction

internal class GitLabMergeRequestSquashAndMergeAction(
  scope: CoroutineScope,
  private val reviewFlowVm: GitLabMergeRequestReviewFlowViewModel
) : AbstractAction(CollaborationToolsBundle.message("review.details.action.squash.and.merge")) {
  init {
    scope.launch {
      combineAndCollect(
        reviewFlowVm.isBusy,
        reviewFlowVm.requestState,
        reviewFlowVm.userCanMergeReviewer
      ) { isBusy, requestState, userCanMergeReviewer ->
        isEnabled = !isBusy && requestState == RequestState.OPENED && userCanMergeReviewer
      }
    }
  }

  override fun actionPerformed(e: ActionEvent?) = reviewFlowVm.squashAndMerge()
}