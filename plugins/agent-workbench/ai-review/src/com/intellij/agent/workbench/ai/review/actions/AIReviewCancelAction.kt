// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.ai.review.actions

import com.intellij.agent.workbench.ai.review.model.AIReviewViewModel
import com.intellij.agent.workbench.ai.review.ui.AIReviewProblemsViewPanel
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction

internal class AIReviewCancelAction : DumbAwareAction() {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    val panel = e.getData(AIReviewProblemsViewPanel.PANEL_KEY)
    val session = panel?.session
    e.presentation.isEnabledAndVisible = session != null && session.hasRunningReview()
  }

  override fun actionPerformed(e: AnActionEvent) {
    val panel = e.getData(AIReviewProblemsViewPanel.PANEL_KEY) ?: return
    panel.session.cancelRunningReview()
  }
}

internal fun isBusy(viewModel: AIReviewViewModel): Boolean {
  val state = viewModel.state.value
  return state is AIReviewViewModel.State.Running
         || state is AIReviewViewModel.State.PartialReviewReceived
}
