// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui.details

import com.intellij.collaboration.async.launchNow
import com.intellij.collaboration.messages.CollaborationToolsBundle
import com.intellij.collaboration.ui.HorizontalListPanel
import com.intellij.collaboration.ui.codereview.details.CodeReviewDetailsActionsComponentFactory
import com.intellij.collaboration.ui.codereview.details.CodeReviewDetailsActionsComponentFactory.CodeReviewActions
import com.intellij.collaboration.ui.codereview.details.data.ReviewRole
import com.intellij.collaboration.ui.codereview.details.data.ReviewState
import com.intellij.collaboration.ui.icon.IconsProvider
import com.intellij.collaboration.ui.util.bindContentIn
import com.intellij.collaboration.ui.util.bindVisibilityIn
import com.intellij.collaboration.ui.util.toAnAction
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.ui.components.JBOptionButton
import com.intellij.ui.components.panels.Wrapper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.jetbrains.plugins.gitlab.api.dto.GitLabUserDTO
import org.jetbrains.plugins.gitlab.mergerequest.action.*
import org.jetbrains.plugins.gitlab.mergerequest.ui.GitLabMergeRequestSubmitReviewPopup
import org.jetbrains.plugins.gitlab.mergerequest.ui.details.model.GitLabMergeRequestReviewFlowViewModel
import org.jetbrains.plugins.gitlab.util.GitLabBundle
import javax.swing.JButton
import javax.swing.JComponent

internal object GitLabMergeRequestDetailsActionsComponentFactory {
  private const val BUTTONS_GAP = 10

  fun create(
    scope: CoroutineScope,
    reviewFlowVm: GitLabMergeRequestReviewFlowViewModel,
    avatarIconsProvider: IconsProvider<GitLabUserDTO>
  ): JComponent {
    val reviewActions = CodeReviewActions(
      requestReviewAction = GitLabMergeRequestRequestReviewAction(scope, reviewFlowVm, avatarIconsProvider),
      reRequestReviewAction = GitLabMergeRequestReRequestReviewAction(),
      closeReviewAction = GitLabMergeRequestCloseAction(scope, reviewFlowVm),
      reopenReviewAction = GitLabMergeRequestReopenAction(scope, reviewFlowVm),
      setMyselfAsReviewerAction = GitLabMergeRequestSetMyselfAsReviewerAction(scope, reviewFlowVm),
      postReviewAction = GitLabMergeRequestPostReviewAction(scope, reviewFlowVm),
      mergeReviewAction = GitLabMergeRequestMergeAction(scope, reviewFlowVm),
      mergeSquashReviewAction = GitLabMergeRequestSquashAndMergeAction(scope, reviewFlowVm),
      rebaseReviewAction = GitLabMergeRequestRebaseAction()
    )
    val moreActionsGroup = DefaultActionGroup(GitLabBundle.message("merge.request.details.action.review.more.text"), true)

    return Wrapper().apply {
      bindContentIn(scope, reviewFlowVm.role) { role ->
        val mainPanel = when (role) {
          ReviewRole.AUTHOR -> createActionsForAuthor(reviewFlowVm.reviewState, reviewFlowVm.reviewers, reviewActions, moreActionsGroup)
          ReviewRole.REVIEWER -> createActionsForReviewer(reviewFlowVm, reviewActions, moreActionsGroup)
          ReviewRole.GUEST -> CodeReviewDetailsActionsComponentFactory.createActionsForGuest(reviewActions, moreActionsGroup,
                                                                                             ::createMergeActionGroup)
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
    reviewFlowVm: GitLabMergeRequestReviewFlowViewModel,
    reviewActions: CodeReviewActions,
    moreActionsGroup: DefaultActionGroup
  ): JComponent {
    val submitButton = JButton(GitLabMergeRequestSubmitReviewAction(this, reviewFlowVm)).apply {
      toolTipText = GitLabBundle.message("merge.request.review.submit.action.tooltip")
    }
    reviewFlowVm.submitReviewInputHandler = {
      GitLabMergeRequestSubmitReviewPopup.show(it, submitButton, true)
    }

    val moreActionsButton = CodeReviewDetailsActionsComponentFactory.createMoreButton(moreActionsGroup)
    launchNow {
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
      add(submitButton)
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