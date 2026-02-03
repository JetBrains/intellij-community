// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.ui.details.action

import com.intellij.collaboration.messages.CollaborationToolsBundle
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestRequestedReviewer
import org.jetbrains.plugins.github.pullrequest.ui.details.model.GHPRReviewFlowViewModel
import java.awt.event.ActionEvent
import javax.swing.AbstractAction

internal class GHPRRemoveReviewerAction(
  scope: CoroutineScope,
  private val project: Project,
  private val reviewFlowVm: GHPRReviewFlowViewModel,
  private val reviewer: GHPullRequestRequestedReviewer
) : AbstractAction(CollaborationToolsBundle.message("review.details.action.remove.reviewer", reviewer.name ?: reviewer.shortName)) {

  init {
    scope.launch {
      reviewFlowVm.isBusy.collect { isBusy ->
        isEnabled = !isBusy && reviewFlowVm.userCanManageReview
      }
    }
  }

  override fun actionPerformed(event: ActionEvent) = reviewFlowVm.removeReviewer(reviewer)
}