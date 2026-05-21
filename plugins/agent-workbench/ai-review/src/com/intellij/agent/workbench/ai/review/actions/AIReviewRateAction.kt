// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.ai.review.actions

import com.intellij.agent.workbench.ai.review.AIReviewCollector
import com.intellij.agent.workbench.ai.review.model.AIReviewViewModel
import com.intellij.agent.workbench.ai.review.model.ReviewRating
import com.intellij.agent.workbench.ai.review.ui.AIReviewProblemsViewPanel
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.util.IconUtil
import kotlinx.coroutines.flow.update
import javax.swing.Icon

internal sealed class AIReviewRateAction : DumbAwareAction() {

  abstract fun getReaction(): ReviewRating
  abstract fun getReactionIcon(): Icon
  abstract fun getReactionIconSelected(): Icon

  override fun getActionUpdateThread() = ActionUpdateThread.EDT // wrapped by ActionButton

  override fun update(e: AnActionEvent) {
    super.update(e)
    val panel = e.getData(AIReviewProblemsViewPanel.PANEL_KEY)
    val session = panel?.session

    if (session == null) {
      e.presentation.isEnabledAndVisible = false
      return
    }

    val viewModel = session.viewModel
    val icon = if (!isReactionSelected(viewModel)) getReactionIcon() else getReactionIconSelected()
    e.presentation.icon = icon

    val failedOrCanceled = isFailureOrCanceled(viewModel)
    val reviewInProgress = isBusy(viewModel)

    e.presentation.isEnabledAndVisible = !reviewInProgress && !failedOrCanceled
  }

  override fun actionPerformed(e: AnActionEvent) {
    val panel = e.getData(AIReviewProblemsViewPanel.PANEL_KEY) ?: return
    val viewModel = panel.session.viewModel

    val rating = if (isReactionSelected(viewModel)) ReviewRating.None else getReaction()
    viewModel.rating.update { rating }
    AIReviewCollector.logFeedback(panel.session.project, viewModel.getCurrentRequest().requestId, rating)
  }

  private fun isReactionSelected(viewModel: AIReviewViewModel): Boolean {
    return viewModel.rating.value == getReaction()
  }

  class Like : AIReviewRateAction() {
    private val reactionIcon = scaleFeedbackIcon(AllIcons.Ide.Like)
    private val reactionIconSelected = scaleFeedbackIcon(AllIcons.Ide.LikeSelected)

    override fun getReaction() = ReviewRating.Like
    override fun getReactionIcon(): Icon = reactionIcon
    override fun getReactionIconSelected(): Icon = reactionIconSelected
  }

  class Dislike : AIReviewRateAction() {
    private val reactionIcon = scaleFeedbackIcon(AllIcons.Ide.Dislike)
    private val reactionIconSelected = scaleFeedbackIcon(AllIcons.Ide.DislikeSelected)

    override fun getReaction() = ReviewRating.Dislike
    override fun getReactionIcon(): Icon = reactionIcon
    override fun getReactionIconSelected(): Icon = reactionIconSelected
  }

  companion object {
    private const val FEEDBACK_ICON_SIZE = 16

    internal fun isFailureOrCanceled(viewModel: AIReviewViewModel): Boolean {
      val state = viewModel.state.value
      return state is AIReviewViewModel.State.Error
             || state is AIReviewViewModel.State.Cancelled
    }

    private fun scaleFeedbackIcon(icon: Icon): Icon {
      return IconUtil.scale(icon, null, FEEDBACK_ICON_SIZE.toFloat() / icon.iconWidth)
    }
  }
}
