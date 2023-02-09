// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.ui.details.action

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.plugins.github.i18n.GithubBundle
import org.jetbrains.plugins.github.pullrequest.ui.details.model.GHPRReviewFlowViewModel
import java.awt.event.ActionEvent
import javax.swing.AbstractAction

internal class GHPRCommitMergeAction(scope: CoroutineScope, private val reviewFlowVm: GHPRReviewFlowViewModel)
  : AbstractAction(GithubBundle.message("pull.request.merge.commit.action")) {

  init {
    scope.launch {
      reviewFlowVm.isBusy.collect { isBusy ->
        isEnabled = !isBusy && reviewFlowVm.isMergeAllowed && reviewFlowVm.userCanMergeReview
      }
    }
  }

  override fun actionPerformed(e: ActionEvent?) = reviewFlowVm.mergeReview()
}