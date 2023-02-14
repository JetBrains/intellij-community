// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.ui.details.action

import com.intellij.collaboration.async.combineAndCollect
import com.intellij.collaboration.messages.CollaborationToolsBundle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.plugins.github.pullrequest.ui.details.model.GHPRReviewFlowViewModel
import java.awt.event.ActionEvent
import javax.swing.AbstractAction

internal class GHPRSquashMergeAction(scope: CoroutineScope, private val reviewFlowVm: GHPRReviewFlowViewModel)
  : AbstractAction(CollaborationToolsBundle.message("review.details.action.squash.and.merge")) {

  init {
    scope.launch {
      combineAndCollect(reviewFlowVm.isBusy, reviewFlowVm.isSquashMergeAllowed) { isBusy, isSquashMergeAllowed ->
        isEnabled = !isBusy && isSquashMergeAllowed && reviewFlowVm.userCanMergeReview
      }
    }
  }

  override fun actionPerformed(e: ActionEvent?) = reviewFlowVm.squashAndMergeReview()
}