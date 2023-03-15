// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.ui.details

import com.intellij.collaboration.messages.CollaborationToolsBundle
import com.intellij.collaboration.ui.HorizontalListPanel
import com.intellij.collaboration.ui.codereview.details.RequestState
import com.intellij.collaboration.ui.codereview.details.ReviewRole
import com.intellij.collaboration.ui.codereview.details.ReviewState
import com.intellij.collaboration.ui.util.bindText
import com.intellij.collaboration.ui.util.bindVisibility
import com.intellij.collaboration.ui.util.toAnAction
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.CustomComponentAction
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.application.EDT
import com.intellij.ui.CardLayoutPanel
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.components.JBOptionButton
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.childScope
import com.intellij.util.ui.InlineIconButton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.jetbrains.plugins.github.i18n.GithubBundle
import org.jetbrains.plugins.github.pullrequest.action.GHPRActionKeys
import org.jetbrains.plugins.github.pullrequest.action.GHPRReviewSubmitAction
import org.jetbrains.plugins.github.pullrequest.data.provider.GHPRDataProvider
import org.jetbrains.plugins.github.pullrequest.ui.details.action.*
import org.jetbrains.plugins.github.pullrequest.ui.details.model.GHPRDetailsViewModel
import org.jetbrains.plugins.github.pullrequest.ui.details.model.GHPRReviewFlowViewModel
import java.awt.event.ActionListener
import javax.swing.Action
import javax.swing.JButton
import javax.swing.JComponent

internal class GHPRStatePanel(
  parentScope: CoroutineScope,
  private val reviewDetailsVm: GHPRDetailsViewModel,
  private val reviewFlowVm: GHPRReviewFlowViewModel,
  private val dataProvider: GHPRDataProvider
) : CardLayoutPanel<ReviewRole, GHPRStatePanel.StateUI, JComponent>() {
  private val scope = parentScope.childScope(Dispatchers.EDT)

  init {
    scope.launch {
      reviewFlowVm.role.collect { role ->
        select(role, true)
      }
    }
  }

  override fun prepare(key: ReviewRole): StateUI {
    return when (key) {
      ReviewRole.AUTHOR -> StateUI.Author(scope, reviewDetailsVm, reviewFlowVm)
      ReviewRole.REVIEWER -> StateUI.Reviewer(scope, reviewDetailsVm, reviewFlowVm, dataProvider)
      ReviewRole.GUEST -> StateUI.Guest(scope, reviewDetailsVm, reviewFlowVm)
    }
  }

  override fun create(ui: StateUI) = ui.createComponent()

  internal sealed class StateUI(
    protected val scope: CoroutineScope,
    private val reviewDetailsVm: GHPRDetailsViewModel,
    protected val reviewFlowVm: GHPRReviewFlowViewModel
  ) {
    abstract fun createReviewActionsForOpenReview(): JComponent

    fun createComponent(): JComponent {
      val reviewActionsComponentForOpenReview = createReviewActionsForOpenReview().apply {
        bindVisibility(scope, reviewDetailsVm.requestState.map { it == RequestState.OPENED })
      }
      val reviewActionsComponentForCloseReview = JButton().apply {
        isOpaque = false
        text = GithubBundle.message("pull.request.reopen.action")
        action = GHPRReopenAction(scope, reviewFlowVm)
        bindVisibility(scope, reviewDetailsVm.requestState.map { it == RequestState.CLOSED })
      }
      val reviewActionsComponentForDraftReview = JButton(GHPRPostReviewAction(scope, reviewFlowVm)).apply {
        isOpaque = false
        bindVisibility(scope, reviewDetailsVm.requestState.map { it == RequestState.DRAFT })
      }

      return HorizontalListPanel().apply {
        add(reviewActionsComponentForOpenReview)
        add(reviewActionsComponentForCloseReview)
        add(reviewActionsComponentForDraftReview)
      }
    }

    protected fun createMoreButton(actionGroup: ActionGroup): JComponent {
      return InlineIconButton(AllIcons.Actions.More).apply {
        withBackgroundHover = true
        actionListener = ActionListener { event ->
          val parentComponent = event.source as JComponent
          val popupMenu = ActionManager.getInstance().createActionPopupMenu("github.review.details", actionGroup)
          val point = RelativePoint.getSouthWestOf(parentComponent).originalPoint
          popupMenu.component.show(parentComponent, point.x, point.y + JBUIScale.scale(8))
        }
      }
    }

    protected fun createMergeReviewButton(scope: CoroutineScope, reviewFlowVm: GHPRReviewFlowViewModel): JComponent {
      val actions = arrayOf<Action>(
        GHPRRebaseMergeAction(scope, reviewFlowVm),
        GHPRSquashMergeAction(scope, reviewFlowVm)
      )
      return JBOptionButton(GHPRCommitMergeAction(scope, reviewFlowVm), actions).apply {
        bindVisibility(scope, reviewFlowVm.reviewState.map { it == ReviewState.ACCEPTED })
      }
    }

    protected fun createMergeActionGroup(scope: CoroutineScope, reviewFlowVm: GHPRReviewFlowViewModel): ActionGroup {
      return DefaultActionGroup(GithubBundle.message("pull.request.merge.commit.action"), true).apply {
        add(GHPRCommitMergeAction(scope, reviewFlowVm).toAnAction())
        add(GHPRRebaseMergeAction(scope, reviewFlowVm).toAnAction())
        add(GHPRSquashMergeAction(scope, reviewFlowVm).toAnAction())
      }
    }

    class Author(
      scope: CoroutineScope,
      reviewDetailsVm: GHPRDetailsViewModel,
      reviewFlowVm: GHPRReviewFlowViewModel
    ) : StateUI(scope, reviewDetailsVm, reviewFlowVm) {
      override fun createReviewActionsForOpenReview(): JComponent {
        val requestReviewButton = JButton().apply {
          isOpaque = false
          text = CollaborationToolsBundle.message("review.details.action.request")
          action = GHPRRequestReviewAction(scope, reviewFlowVm)
          bindVisibility(scope, combine(reviewFlowVm.reviewState, reviewFlowVm.requestedReviewers) { reviewState, requestedReviewers ->
            reviewState == ReviewState.NEED_REVIEW ||
            (reviewState == ReviewState.WAIT_FOR_UPDATES && requestedReviewers.isNotEmpty())
          })
        }
        val reRequestReviewButton = JButton().apply {
          isOpaque = false
          text = CollaborationToolsBundle.message("review.details.action.rerequest")
          action = GHPRReRequestReviewAction(scope, reviewFlowVm)
          bindVisibility(scope, combine(reviewFlowVm.reviewState, reviewFlowVm.requestedReviewers) { reviewState, requestedReviewers ->
            reviewState == ReviewState.WAIT_FOR_UPDATES && requestedReviewers.isEmpty()
          })
        }
        val mergePullRequest = createMergeReviewButton(scope, reviewFlowVm)

        val actionGroup = DefaultActionGroup(GithubBundle.message("pull.request.review.actions.more.name"), true)
        val moreActions = createMoreButton(actionGroup)

        scope.launch {
          reviewFlowVm.reviewState.collect { reviewState ->
            actionGroup.removeAll()
            when (reviewState) {
              ReviewState.NEED_REVIEW, ReviewState.WAIT_FOR_UPDATES -> {
                actionGroup.add(createMergeActionGroup(scope, reviewFlowVm))
                actionGroup.add(GHPRCloseAction(scope, reviewFlowVm).toAnAction())
              }
              ReviewState.ACCEPTED -> {
                actionGroup.add(GHPRRequestReviewAction(scope, reviewFlowVm).toAnAction())
                actionGroup.add(GHPRCloseAction(scope, reviewFlowVm).toAnAction())
              }
            }
          }
        }

        return HorizontalListPanel().apply {
          add(requestReviewButton)
          add(reRequestReviewButton)
          add(mergePullRequest)
          add(moreActions)
        }
      }
    }

    class Reviewer(
      scope: CoroutineScope,
      reviewDetailsVm: GHPRDetailsViewModel,
      reviewFlowVm: GHPRReviewFlowViewModel,
      private val dataProvider: GHPRDataProvider
    ) : StateUI(scope, reviewDetailsVm, reviewFlowVm) {
      override fun createReviewActionsForOpenReview(): JComponent {
        val submitReviewButton = createSubmitReviewButton(scope, reviewFlowVm, dataProvider)
        val mergeReviewButton = createMergeReviewButton(scope, reviewFlowVm)

        val actionGroup = DefaultActionGroup(GithubBundle.message("pull.request.review.actions.more.name"), true)
        val moreActions = createMoreButton(actionGroup)

        scope.launch {
          reviewFlowVm.reviewState.collect { reviewState ->
            actionGroup.removeAll()
            when (reviewState) {
              ReviewState.NEED_REVIEW, ReviewState.WAIT_FOR_UPDATES -> {
                actionGroup.add(GHPRRequestReviewAction(scope, reviewFlowVm).toAnAction())
                actionGroup.add(createMergeActionGroup(scope, reviewFlowVm))
                actionGroup.add(GHPRCloseAction(scope, reviewFlowVm).toAnAction())
              }
              ReviewState.ACCEPTED -> {
                actionGroup.add(GHPRRequestReviewAction(scope, reviewFlowVm).toAnAction())
                actionGroup.add(GHPRCloseAction(scope, reviewFlowVm).toAnAction())
              }
            }
          }
        }

        return HorizontalListPanel().apply {
          add(submitReviewButton)
          add(mergeReviewButton)
          add(moreActions)
        }
      }

      private fun createSubmitReviewButton(
        scope: CoroutineScope,
        reviewFlowVm: GHPRReviewFlowViewModel,
        dataProvider: GHPRDataProvider
      ): JComponent {
        return JButton().apply {
          isOpaque = false
          addActionListener {
            val dataContext = SimpleDataContext.builder()
              .add(GHPRActionKeys.PULL_REQUEST_DATA_PROVIDER, dataProvider)
              .build()
            val presentation = Presentation()
            presentation.putClientProperty(CustomComponentAction.COMPONENT_KEY, this)
            val anActionEvent = AnActionEvent.createFromDataContext("github.review.details", presentation, dataContext)
            GHPRReviewSubmitAction().actionPerformed(anActionEvent)
          }

          bindText(scope, reviewFlowVm.pendingComments.map { pendingComments ->
            GithubBundle.message("pull.request.review.actions.submit", pendingComments)
          })
          bindVisibility(scope, reviewFlowVm.reviewState.map {
            it == ReviewState.WAIT_FOR_UPDATES || it == ReviewState.NEED_REVIEW
          })
        }
      }
    }

    class Guest(
      scope: CoroutineScope,
      reviewDetailsVm: GHPRDetailsViewModel,
      reviewFlowVm: GHPRReviewFlowViewModel
    ) : StateUI(scope, reviewDetailsVm, reviewFlowVm) {
      override fun createReviewActionsForOpenReview(): JComponent {
        val setAsReviewerButton = JButton().apply {
          isOpaque = false
          text = CollaborationToolsBundle.message("review.details.action.set.myself.as.reviewer")
          action = GHPRSetMyselfAsReviewerAction(scope, reviewFlowVm)
        }
        val actionGroup = DefaultActionGroup(GithubBundle.message("pull.request.review.actions.more.name"), true).apply {
          add(GHPRRequestReviewAction(scope, reviewFlowVm).toAnAction())
          add(createMergeActionGroup(scope, reviewFlowVm))
          add(GHPRCloseAction(scope, reviewFlowVm).toAnAction())
        }
        val moreButton = createMoreButton(actionGroup)

        return HorizontalListPanel().apply {
          add(setAsReviewerButton)
          add(moreButton)
        }
      }
    }
  }
}