// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui.details

import com.intellij.collaboration.ui.HorizontalListPanel
import com.intellij.collaboration.ui.codereview.details.CodeReviewDetailsActionsComponentFactory
import com.intellij.collaboration.ui.codereview.details.data.ReviewRole
import com.intellij.collaboration.ui.codereview.details.data.ReviewState
import com.intellij.collaboration.ui.icon.IconsProvider
import com.intellij.collaboration.ui.util.bindContentIn
import com.intellij.collaboration.ui.util.bindVisibilityIn
import com.intellij.collaboration.ui.util.toAnAction
import com.intellij.ide.plugins.newui.InstallButton
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.ui.components.panels.Wrapper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.jetbrains.plugins.gitlab.api.dto.GitLabUserDTO
import org.jetbrains.plugins.gitlab.mergerequest.action.*
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
    val reviewActions = CodeReviewDetailsActionsComponentFactory.CodeReviewActions(
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
      bindContentIn(scope, reviewFlowVm.role.map { role ->
        val mainPanel = when (role) {
          ReviewRole.AUTHOR -> CodeReviewDetailsActionsComponentFactory.createActionsForAuthor(
            scope, reviewFlowVm.reviewState, reviewFlowVm.reviewers, reviewActions, moreActionsGroup
          )
          ReviewRole.REVIEWER -> createActionsForReviewer(scope, reviewFlowVm, reviewActions, moreActionsGroup)
          ReviewRole.GUEST -> CodeReviewDetailsActionsComponentFactory.createActionsForGuest(reviewActions, moreActionsGroup)
        }

        return@map CodeReviewDetailsActionsComponentFactory.createActionsComponent(
          scope, reviewFlowVm.reviewRequestState,
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
    reviewFlowVm: GitLabMergeRequestReviewFlowViewModel,
    reviewActions: CodeReviewDetailsActionsComponentFactory.CodeReviewActions,
    moreActionsGroup: DefaultActionGroup
  ): JComponent {
    val approveButton = object : InstallButton(true) {
      override fun setTextAndSize() {}
    }.apply {
      action = GitLabMergeRequestApproveAction(scope, reviewFlowVm)
      bindVisibilityIn(scope, reviewFlowVm.approvedBy.map { reviewFlowVm.currentUser !in it })
    }
    val resumeReviewButton = JButton(GitLabMergeRequestReviewResumeAction(scope, reviewFlowVm)).apply {
      isOpaque = false
      bindVisibilityIn(scope, reviewFlowVm.approvedBy.map { reviewFlowVm.currentUser in it })
    }

    val moreActionsButton = CodeReviewDetailsActionsComponentFactory.createMoreButton(moreActionsGroup)
    scope.launch(start = CoroutineStart.UNDISPATCHED) {
      reviewFlowVm.reviewState.collect { reviewState ->
        moreActionsGroup.removeAll()
        when (reviewState) {
          ReviewState.NEED_REVIEW, ReviewState.WAIT_FOR_UPDATES -> {
            moreActionsGroup.add(reviewActions.requestReviewAction.toAnAction())
            moreActionsGroup.add(CodeReviewDetailsActionsComponentFactory.createMergeActionGroup(reviewActions))
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
      add(approveButton)
      add(resumeReviewButton)
      add(moreActionsButton)
    }
  }
}