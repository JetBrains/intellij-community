// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.action

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabMergeRequestState
import org.jetbrains.plugins.gitlab.mergerequest.ui.details.model.GitLabMergeRequestReviewFlowViewModel
import org.jetbrains.plugins.gitlab.util.GitLabBundle
import java.awt.event.ActionEvent

internal class GitLabMergeRequestMergeAction(
  scope: CoroutineScope,
  private val reviewFlowVm: GitLabMergeRequestReviewFlowViewModel
) : GitLabMergeRequestAction(GitLabBundle.message("merge.request.details.action.review.merge.text"), scope, reviewFlowVm) {
  init {
    scope.launch {
      reviewFlowVm.state.collect {
        update()
      }
    }
  }

  override fun actionPerformed(e: ActionEvent?) = reviewFlowVm.merge()

  override fun enableCondition(): Boolean {
    return reviewFlowVm.state.value == GitLabMergeRequestState.OPENED // TODO: add conditions
  }
}