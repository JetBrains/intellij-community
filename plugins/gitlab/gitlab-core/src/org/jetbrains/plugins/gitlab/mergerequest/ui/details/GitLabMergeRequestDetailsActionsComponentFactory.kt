// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui.details

import com.intellij.collaboration.async.awaitCancelling
import com.intellij.collaboration.async.launchNow
import com.intellij.collaboration.messages.CollaborationToolsBundle
import com.intellij.collaboration.ui.HorizontalListPanel
import com.intellij.collaboration.ui.codereview.action.AutoDisablingActionGroup
import com.intellij.collaboration.ui.codereview.details.CodeReviewDetailsActionsComponentFactory
import com.intellij.collaboration.ui.codereview.details.CodeReviewDetailsActionsComponentFactory.CodeReviewActions
import com.intellij.collaboration.ui.codereview.details.data.ReviewRole
import com.intellij.collaboration.ui.codereview.details.data.ReviewState
import com.intellij.collaboration.ui.util.bindContentIn
import com.intellij.collaboration.ui.util.bindVisibilityIn
import com.intellij.collaboration.ui.util.toAnAction
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.ui.components.JBOptionButton
import com.intellij.ui.components.panels.Wrapper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import org.jetbrains.plugins.gitlab.mergerequest.action.*
import org.jetbrains.plugins.gitlab.mergerequest.ui.details.model.GitLabMergeRequestReviewFlowViewModel
import org.jetbrains.plugins.gitlab.mergerequest.ui.details.model.GitLabMergeRequestReviewFlowViewModelImpl.Companion.toReviewState
import org.jetbrains.plugins.gitlab.mergerequest.ui.review.GitLabMergeRequestSubmitReviewPopup
import org.jetbrains.plugins.gitlab.util.GitLabBundle
import javax.swing.JButton
import javax.swing.JComponent

internal object GitLabMergeRequestDetailsActionsComponentFactory {
  private const val BUTTONS_GAP = 10

  fun create(scope: CoroutineScope, reviewFlowVm: GitLabMergeRequestReviewFlowViewModel): JComponent {
    val reviewActions = CodeReviewActions(
      requestReviewAction = GitLabMergeRequestRequestReviewAction(scope, reviewFlowVm),
      reRequestReviewAction = GitLabMergeRequestReRequestReviewAction(scope, reviewFlowVm),
      closeReviewAction = GitLabMergeRequestCloseAction(scope, reviewFlowVm),
      reopenReviewAction = GitLabMergeRequestReopenAction(scope, reviewFlowVm),
      setMyselfAsReviewerAction = GitLabMergeRequestSetMyselfAsReviewerAction(scope, reviewFlowVm),
      postReviewAction = GitLabMergeRequestPostReviewAction(scope, reviewFlowVm),
      mergeReviewAction = GitLabMergeRequestMergeAction(scope, reviewFlowVm),
      mergeSquashReviewAction = GitLabMergeRequestSquashAndMergeAction(scope, reviewFlowVm),
      rebaseReviewAction = GitLabMergeRequestRebaseAction(scope, reviewFlowVm)
    )

    return Wrapper().apply {
      bindContentIn(scope, reviewFlowVm.role.distinctUntilChanged()) { role ->
        val moreActionsGroup = DefaultActionGroup(GitLabBundle.message("merge.request.details.action.review.more.text"), true)
        val mainPanel = when (role) {
          ReviewRole.AUTHOR -> createActionsForAuthor(reviewFlowVm, reviewActions, moreActionsGroup)
          ReviewRole.REVIEWER -> createActionsForReviewer(reviewFlowVm, reviewActions, moreActionsGroup)
          ReviewRole.GUEST -> CodeReviewDetailsActionsComponentFactory
            .createActionsForGuest(reviewActions, moreActionsGroup, ::createMergeActionGroup)
        }

        CodeReviewDetailsActionsComponentFactory.createActionsComponent(
          this, reviewFlowVm.reviewRequestState,
          openedStatePanel = mainPanel,
          CodeReviewDetailsActionsComponentFactory.createActionsForMergedReview(),
          CodeReviewDetailsActionsComponentFactory.createActionsForClosedReview(reviewActions.reopenReviewAction),
          CodeReviewDetailsActionsComponentFactory.createActionsForDraftReview(reviewActions.postReviewAction)
        )
      }
    }
  }

  private fun CoroutineScope.createActionsForAuthor(
    vm: GitLabMergeRequestReviewFlowViewModel,
    reviewActions: CodeReviewActions,
    moreActionsGroup: DefaultActionGroup
  ): JComponent {
    val cs = this
    val reviewState = vm.reviewState
    val requestedReviewers = vm.reviewers.map { reviewers ->
      reviewers.filter { it.mergeRequestInteraction.toReviewState() == ReviewState.NEED_REVIEW }
    }
    val requestReviewButton = CodeReviewDetailsActionsComponentFactory.createRequestReviewButton(
      cs, reviewState, requestedReviewers, reviewActions.requestReviewAction
    )
    val reRequestReviewButton = CodeReviewDetailsActionsComponentFactory.createReRequestReviewButton(
      cs, reviewState, requestedReviewers, reviewActions.reRequestReviewAction
    )
    val mergeReviewButton = JBOptionButton(reviewActions.mergeReviewAction, arrayOf(reviewActions.mergeSquashReviewAction)).apply {
      bindVisibilityIn(cs, reviewState.map { it == ReviewState.ACCEPTED })
    }
    val moreActionsButton = CodeReviewDetailsActionsComponentFactory.createMoreButton(moreActionsGroup)
    cs.launchNow {
      reviewState.collect { reviewState ->
        moreActionsGroup.removeAll()
        when (reviewState) {
          ReviewState.NEED_REVIEW, ReviewState.WAIT_FOR_UPDATES -> {
            moreActionsGroup.add(createMergeActionGroup(reviewActions))
            moreActionsGroup.add(reviewActions.rebaseReviewAction.toAnAction())
            moreActionsGroup.add(reviewActions.closeReviewAction.toAnAction())
          }
          ReviewState.ACCEPTED -> {
            moreActionsGroup.add(reviewActions.requestReviewAction.toAnAction())
            moreActionsGroup.add(reviewActions.rebaseReviewAction.toAnAction())
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
    reviewFlowVm: GitLabMergeRequestReviewFlowViewModel,
    reviewActions: CodeReviewActions,
    moreActionsGroup: DefaultActionGroup
  ): JComponent {
    val cs = this
    val submitButton = JButton(GitLabMergeRequestSubmitReviewAction(cs, reviewFlowVm)).apply {
      toolTipText = GitLabBundle.message("merge.request.review.submit.action.tooltip")
    }
    reviewFlowVm.submitReviewInputHandler = {
      cs.async {
        GitLabMergeRequestSubmitReviewPopup.show(it, submitButton, true)
      }.awaitCancelling()
    }

    moreActionsGroup.add(reviewActions.requestReviewAction.toAnAction())
    moreActionsGroup.add(createMergeActionGroup(reviewActions))
    moreActionsGroup.add(reviewActions.closeReviewAction.toAnAction())
    val moreActionsButton = CodeReviewDetailsActionsComponentFactory.createMoreButton(moreActionsGroup)

    return HorizontalListPanel(BUTTONS_GAP).apply {
      add(submitButton)
      add(moreActionsButton)
    }
  }

  private fun createMergeActionGroup(reviewActions: CodeReviewActions): ActionGroup {
    return AutoDisablingActionGroup(CollaborationToolsBundle.message("review.details.action.merge.group"), true).apply {
      add(reviewActions.mergeReviewAction.toAnAction())
      add(reviewActions.mergeSquashReviewAction.toAnAction())
    }
  }
}