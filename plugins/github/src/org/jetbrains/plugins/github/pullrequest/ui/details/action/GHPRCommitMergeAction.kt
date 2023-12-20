// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.ui.details.action

import com.intellij.collaboration.async.combineAndCollect
import com.intellij.collaboration.messages.CollaborationToolsBundle
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.plugins.github.pullrequest.GHPRAction
import org.jetbrains.plugins.github.pullrequest.GHPRStatisticsCollector
import org.jetbrains.plugins.github.pullrequest.ui.details.model.GHPRReviewFlowViewModel
import java.awt.event.ActionEvent
import javax.swing.AbstractAction
import javax.swing.JButton

internal class GHPRCommitMergeAction(scope: CoroutineScope, private val project: Project, private val reviewFlowVm: GHPRReviewFlowViewModel)
  : AbstractAction(CollaborationToolsBundle.message("review.details.action.merge")) {

  init {
    scope.launch {
      combineAndCollect(reviewFlowVm.isBusy, reviewFlowVm.isMergeEnabled) { isBusy, isMergeEnabled ->
        isEnabled = !isBusy && isMergeEnabled
      }
    }
  }

  override fun actionPerformed(e: ActionEvent?) {
    GHPRStatisticsCollector.logDetailsActionInvoked(project, GHPRAction.MERGE, e?.source is JButton)
    reviewFlowVm.mergeReview()
  }
}