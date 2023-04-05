// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.ui.details

import com.intellij.collaboration.ui.HorizontalListPanel
import com.intellij.collaboration.ui.codereview.details.CodeReviewDetailsActionsComponentFactory
import com.intellij.collaboration.ui.codereview.details.data.ReviewRequestState
import com.intellij.collaboration.ui.codereview.details.data.ReviewRole
import com.intellij.collaboration.ui.codereview.details.data.ReviewState
import com.intellij.collaboration.ui.util.bindContentIn
import com.intellij.collaboration.ui.util.bindTextIn
import com.intellij.collaboration.ui.util.bindVisibilityIn
import com.intellij.collaboration.ui.util.toAnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.ex.CustomComponentAction
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.ui.components.JBOptionButton
import com.intellij.ui.components.panels.Wrapper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.jetbrains.plugins.github.i18n.GithubBundle
import org.jetbrains.plugins.github.pullrequest.action.GHPRActionKeys
import org.jetbrains.plugins.github.pullrequest.action.GHPRReviewSubmitAction
import org.jetbrains.plugins.github.pullrequest.data.provider.GHPRDataProvider
import org.jetbrains.plugins.github.pullrequest.ui.details.action.*
import org.jetbrains.plugins.github.pullrequest.ui.details.model.GHPRReviewFlowViewModel
import javax.swing.JButton
import javax.swing.JComponent

internal object GHPRDetailsActionsComponentFactory {
  private const val BUTTONS_GAP = 10

  fun create(
    scope: CoroutineScope,
    reviewRequestState: Flow<ReviewRequestState>,
    reviewFlowVm: GHPRReviewFlowViewModel,
    dataProvider: GHPRDataProvider
  ): JComponent {
    val reviewActions = CodeReviewDetailsActionsComponentFactory.CodeReviewActions(
      requestReviewAction = GHPRRequestReviewAction(scope, reviewFlowVm),
      reRequestReviewAction = GHPRReRequestReviewAction(scope, reviewFlowVm),
      closeReviewAction = GHPRCloseAction(scope, reviewFlowVm),
      reopenReviewAction = GHPRReopenAction(scope, reviewFlowVm),
      setMyselfAsReviewerAction = GHPRSetMyselfAsReviewerAction(scope, reviewFlowVm),
      postReviewAction = GHPRPostReviewAction(scope, reviewFlowVm),
      mergeReviewAction = GHPRCommitMergeAction(scope, reviewFlowVm),
      mergeSquashReviewAction = GHPRSquashMergeAction(scope, reviewFlowVm),
      rebaseReviewAction = GHPRRebaseMergeAction(scope, reviewFlowVm)
    )
    val moreActionsGroup = DefaultActionGroup(GithubBundle.message("pull.request.merge.commit.action"), true)

    return Wrapper().apply {
      bindContentIn(scope, reviewFlowVm.role.map { role ->
        val mainPanel = when (role) {
          ReviewRole.AUTHOR -> CodeReviewDetailsActionsComponentFactory.createActionsForAuthor(
            scope, reviewFlowVm.reviewState, reviewFlowVm.requestedReviewers, reviewActions, moreActionsGroup
          )
          ReviewRole.REVIEWER -> createActionsForReviewer(scope, reviewFlowVm, dataProvider, reviewActions, moreActionsGroup)
          ReviewRole.GUEST -> CodeReviewDetailsActionsComponentFactory.createActionsForGuest(reviewActions, moreActionsGroup)
        }

        return@map CodeReviewDetailsActionsComponentFactory.createActionsComponent(
          scope, reviewRequestState,
          openedStatePanel = mainPanel,
          CodeReviewDetailsActionsComponentFactory.createActionsForMergedReview(),
          CodeReviewDetailsActionsComponentFactory.createActionsForClosedReview(reviewActions.reopenReviewAction),
          CodeReviewDetailsActionsComponentFactory.createActionsForDraftReview(reviewActions.postReviewAction)
        )
      })
    }
  }

  private fun createActionsForReviewer(
    scope: CoroutineScope,
    reviewFlowVm: GHPRReviewFlowViewModel,
    dataProvider: GHPRDataProvider,
    reviewActions: CodeReviewDetailsActionsComponentFactory.CodeReviewActions,
    moreActionsGroup: DefaultActionGroup
  ): JComponent {
    val submitReviewButton = JButton().apply {
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
      bindTextIn(scope, reviewFlowVm.pendingComments.map { pendingComments ->
        GithubBundle.message("pull.request.review.actions.submit", pendingComments)
      })
      bindVisibilityIn(scope, reviewFlowVm.reviewState.map {
        it == ReviewState.WAIT_FOR_UPDATES || it == ReviewState.NEED_REVIEW
      })
    }
    val mergeReviewButton = JBOptionButton(
      reviewActions.mergeReviewAction,
      arrayOf(reviewActions.mergeSquashReviewAction, reviewActions.rebaseReviewAction)
    ).apply {
      bindVisibilityIn(scope, reviewFlowVm.reviewState.map { it == ReviewState.ACCEPTED })
    }

    val moreActionsButton = CodeReviewDetailsActionsComponentFactory.createMoreButton(moreActionsGroup)
    scope.launch(start = CoroutineStart.UNDISPATCHED) {
      reviewFlowVm.reviewState.collect { reviewState ->
        moreActionsGroup.removeAll()
        when (reviewState) {
          ReviewState.NEED_REVIEW, ReviewState.WAIT_FOR_UPDATES -> {
            moreActionsGroup.add(GHPRRequestReviewAction(scope, reviewFlowVm).toAnAction())
            moreActionsGroup.add(CodeReviewDetailsActionsComponentFactory.createMergeActionGroup(reviewActions))
            moreActionsGroup.add(GHPRCloseAction(scope, reviewFlowVm).toAnAction())
          }
          ReviewState.ACCEPTED -> {
            moreActionsGroup.add(GHPRRequestReviewAction(scope, reviewFlowVm).toAnAction())
            moreActionsGroup.add(GHPRCloseAction(scope, reviewFlowVm).toAnAction())
          }
        }
      }
    }

    return HorizontalListPanel(BUTTONS_GAP).apply {
      add(submitReviewButton)
      add(mergeReviewButton)
      add(moreActionsButton)
    }
  }
}