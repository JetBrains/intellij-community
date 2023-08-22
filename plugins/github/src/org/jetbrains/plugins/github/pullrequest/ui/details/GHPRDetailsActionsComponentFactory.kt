// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.ui.details

import com.intellij.collaboration.messages.CollaborationToolsBundle
import com.intellij.collaboration.ui.HorizontalListPanel
import com.intellij.collaboration.ui.codereview.details.CodeReviewDetailsActionsComponentFactory
import com.intellij.collaboration.ui.codereview.details.CodeReviewDetailsActionsComponentFactory.CodeReviewActions
import com.intellij.collaboration.ui.codereview.details.data.ReviewRequestState
import com.intellij.collaboration.ui.codereview.details.data.ReviewRole
import com.intellij.collaboration.ui.codereview.details.data.ReviewState
import com.intellij.collaboration.ui.util.bindContentIn
import com.intellij.collaboration.ui.util.bindTextIn
import com.intellij.collaboration.ui.util.bindVisibilityIn
import com.intellij.collaboration.ui.util.toAnAction
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.ex.CustomComponentAction
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.project.Project
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
    project: Project,
    reviewRequestState: Flow<ReviewRequestState>,
    reviewFlowVm: GHPRReviewFlowViewModel,
    dataProvider: GHPRDataProvider
  ): JComponent {
    val reviewActions = CodeReviewActions(
      requestReviewAction = GHPRRequestReviewAction(scope, project, reviewFlowVm),
      reRequestReviewAction = GHPRReRequestReviewAction(scope, project, reviewFlowVm),
      closeReviewAction = GHPRCloseAction(scope, project, reviewFlowVm),
      reopenReviewAction = GHPRReopenAction(scope, project, reviewFlowVm),
      setMyselfAsReviewerAction = GHPRSetMyselfAsReviewerAction(scope, project, reviewFlowVm),
      postReviewAction = GHPRPostReviewAction(scope, project, reviewFlowVm),
      mergeReviewAction = GHPRCommitMergeAction(scope, project, reviewFlowVm),
      mergeSquashReviewAction = GHPRSquashMergeAction(scope, project, reviewFlowVm),
      rebaseReviewAction = GHPRRebaseMergeAction(scope, project, reviewFlowVm)
    )
    val moreActionsGroup = DefaultActionGroup(GithubBundle.message("pull.request.merge.commit.action"), true)

    return Wrapper().apply {
      bindContentIn(scope, reviewFlowVm.role) { role ->
        val mainPanel = when (role) {
          ReviewRole.AUTHOR -> createActionsForAuthor(reviewFlowVm.reviewState, reviewFlowVm.requestedReviewers, reviewActions,
                                                      moreActionsGroup)
          ReviewRole.REVIEWER -> createActionsForReviewer(reviewFlowVm, dataProvider, reviewActions, moreActionsGroup)
          ReviewRole.GUEST -> CodeReviewDetailsActionsComponentFactory.createActionsForGuest(reviewActions, moreActionsGroup,
                                                                                             ::createMergeActionGroup)
        }

        CodeReviewDetailsActionsComponentFactory.createActionsComponent(
          this, reviewRequestState,
          openedStatePanel = mainPanel,
          CodeReviewDetailsActionsComponentFactory.createActionsForMergedReview(),
          CodeReviewDetailsActionsComponentFactory.createActionsForClosedReview(reviewActions.reopenReviewAction),
          CodeReviewDetailsActionsComponentFactory.createActionsForDraftReview(reviewActions.postReviewAction)
        )
      }
    }
  }

  private fun <Reviewer> CoroutineScope.createActionsForAuthor(
    reviewState: Flow<ReviewState>,
    requestedReviewers: Flow<List<Reviewer>>,
    reviewActions: CodeReviewActions,
    moreActionsGroup: DefaultActionGroup
  ): JComponent {
    val cs = this
    val requestReviewButton = CodeReviewDetailsActionsComponentFactory.createRequestReviewButton(
      cs, reviewState, requestedReviewers, reviewActions.requestReviewAction
    )
    val reRequestReviewButton = CodeReviewDetailsActionsComponentFactory.createReRequestReviewButton(
      cs, reviewState, requestedReviewers, reviewActions.reRequestReviewAction
    )
    val mergeReviewButton = JBOptionButton(
      reviewActions.mergeReviewAction,
      arrayOf(reviewActions.mergeSquashReviewAction, reviewActions.rebaseReviewAction)
    ).apply {
      bindVisibilityIn(cs, reviewState.map { it == ReviewState.ACCEPTED })
    }
    val moreActionsButton = CodeReviewDetailsActionsComponentFactory.createMoreButton(moreActionsGroup)
    cs.launch(start = CoroutineStart.UNDISPATCHED) {
      reviewState.collect { reviewState ->
        moreActionsGroup.removeAll()
        when (reviewState) {
          ReviewState.NEED_REVIEW, ReviewState.WAIT_FOR_UPDATES -> {
            moreActionsGroup.add(createMergeActionGroup(reviewActions))
            moreActionsGroup.add(reviewActions.closeReviewAction.toAnAction())
          }
          ReviewState.ACCEPTED -> {
            moreActionsGroup.add(reviewActions.requestReviewAction.toAnAction())
            moreActionsGroup.add(reviewActions.closeReviewAction.toAnAction())
          }
        }
      }
    }

    return HorizontalListPanel(BUTTONS_GAP).apply {
      add(requestReviewButton)
      add(reRequestReviewButton)
      add(mergeReviewButton)
      add(moreActionsButton)
    }
  }

  private fun CoroutineScope.createActionsForReviewer(
    reviewFlowVm: GHPRReviewFlowViewModel,
    dataProvider: GHPRDataProvider,
    reviewActions: CodeReviewActions,
    moreActionsGroup: DefaultActionGroup
  ): JComponent {
    val cs = this
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
      bindTextIn(cs, reviewFlowVm.pendingComments.map { pendingComments ->
        GithubBundle.message("pull.request.review.actions.submit", pendingComments)
      })
      bindVisibilityIn(cs, reviewFlowVm.reviewState.map {
        it == ReviewState.WAIT_FOR_UPDATES || it == ReviewState.NEED_REVIEW
      })
    }
    val mergeReviewButton = JBOptionButton(
      reviewActions.mergeReviewAction,
      arrayOf(reviewActions.mergeSquashReviewAction, reviewActions.rebaseReviewAction)
    ).apply {
      bindVisibilityIn(cs, reviewFlowVm.reviewState.map { it == ReviewState.ACCEPTED })
    }

    val moreActionsButton = CodeReviewDetailsActionsComponentFactory.createMoreButton(moreActionsGroup)
    cs.launch(start = CoroutineStart.UNDISPATCHED) {
      reviewFlowVm.reviewState.collect { reviewState ->
        moreActionsGroup.removeAll()
        when (reviewState) {
          ReviewState.NEED_REVIEW, ReviewState.WAIT_FOR_UPDATES -> {
            moreActionsGroup.add(reviewActions.requestReviewAction.toAnAction())
            moreActionsGroup.add(createMergeActionGroup(reviewActions))
            moreActionsGroup.add(reviewActions.closeReviewAction.toAnAction())
          }
          ReviewState.ACCEPTED -> {
            moreActionsGroup.add(reviewActions.requestReviewAction.toAnAction())
            moreActionsGroup.add(reviewActions.closeReviewAction.toAnAction())
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

  private fun createMergeActionGroup(reviewActions: CodeReviewActions): ActionGroup {
    return DefaultActionGroup(CollaborationToolsBundle.message("review.details.action.merge.group"), true).apply {
      add(reviewActions.mergeReviewAction.toAnAction())
      add(reviewActions.mergeSquashReviewAction.toAnAction())
      add(reviewActions.rebaseReviewAction.toAnAction())
    }
  }
}