// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui.details

import com.intellij.collaboration.async.inverted
import com.intellij.collaboration.ui.HorizontalListPanel
import com.intellij.collaboration.ui.codereview.details.ReviewRole
import com.intellij.collaboration.ui.icon.IconsProvider
import com.intellij.collaboration.ui.util.bindContent
import com.intellij.collaboration.ui.util.bindVisibility
import com.intellij.collaboration.ui.util.toAnAction
import com.intellij.icons.AllIcons
import com.intellij.ide.plugins.newui.InstallButton
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.components.panels.Wrapper
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.InlineIconButton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.map
import org.jetbrains.plugins.gitlab.api.dto.GitLabUserDTO
import org.jetbrains.plugins.gitlab.mergerequest.action.*
import org.jetbrains.plugins.gitlab.mergerequest.ui.details.model.GitLabMergeRequestReviewFlowViewModel
import org.jetbrains.plugins.gitlab.util.GitLabBundle
import java.awt.event.ActionListener
import javax.swing.JButton
import javax.swing.JComponent

// TODO: implement `more` button
internal object GitLabMergeRequestDetailsActionsComponentFactory {
  private const val BUTTONS_GAP = 10

  fun create(
    scope: CoroutineScope,
    reviewFlowVm: GitLabMergeRequestReviewFlowViewModel,
    avatarIconsProvider: IconsProvider<GitLabUserDTO>
  ): JComponent {
    return Wrapper().apply {
      bindContent(scope, reviewFlowVm.role.map { role ->
        when (role) {
          ReviewRole.AUTHOR -> createActionsForAuthor(scope, reviewFlowVm, avatarIconsProvider)
          ReviewRole.REVIEWER -> createActionsForReviewer(scope, reviewFlowVm)
          ReviewRole.GUEST -> createActionsForGuest(scope, reviewFlowVm, avatarIconsProvider)
        }
      })
    }
  }

  private fun createActionsForAuthor(
    scope: CoroutineScope,
    reviewFlowVm: GitLabMergeRequestReviewFlowViewModel,
    avatarIconsProvider: IconsProvider<GitLabUserDTO>
  ): JComponent {
    val requestReviewAction = GitLabMergeRequestRequestReviewAction(scope, reviewFlowVm, avatarIconsProvider)
    val mergeReviewAction = GitLabMergeRequestMergeAction(scope, reviewFlowVm)
    val closeReviewAction = GitLabMergeRequestCloseAction(scope, reviewFlowVm)

    val requestReviewButton = JButton(requestReviewAction).apply {
      isOpaque = false
      bindVisibility(scope, reviewFlowVm.isApproved.inverted())
    }
    val mergeReviewButton = JButton(mergeReviewAction).apply {
      isOpaque = false
      bindVisibility(scope, reviewFlowVm.isApproved)
    }
    // TODO: add re-request button

    val actionGroup = DefaultActionGroup(GitLabBundle.message("merge.request.details.action.review.more.text"), true).apply {
      add(if (reviewFlowVm.isApproved.value) requestReviewAction.toAnAction() else mergeReviewAction.toAnAction())
      add(closeReviewAction.toAnAction())
    }
    val moreActionsButton = createMoreButton(actionGroup)

    return HorizontalListPanel(BUTTONS_GAP).apply {
      add(requestReviewButton)
      add(mergeReviewButton)
      add(moreActionsButton)
    }
  }

  private fun createActionsForReviewer(scope: CoroutineScope, reviewFlowVm: GitLabMergeRequestReviewFlowViewModel): JComponent {
    val approveButton = object : InstallButton(true) {
      override fun setTextAndSize() {}
    }.apply {
      action = GitLabMergeRequestApproveAction(scope, reviewFlowVm)
      bindVisibility(scope, reviewFlowVm.approvedBy.map { reviewFlowVm.currentUser !in it })
    }
    val resumeReviewButton = JButton(GitLabMergeRequestReviewResumeAction(scope, reviewFlowVm)).apply {
      isOpaque = false
      bindVisibility(scope, reviewFlowVm.approvedBy.map { reviewFlowVm.currentUser in it })
    }

    return HorizontalListPanel(BUTTONS_GAP).apply {
      add(approveButton)
      add(resumeReviewButton)
    }
  }

  private fun createActionsForGuest(
    scope: CoroutineScope,
    reviewFlowVm: GitLabMergeRequestReviewFlowViewModel,
    avatarIconsProvider: IconsProvider<GitLabUserDTO>
  ): JComponent {
    val requestReviewAction = GitLabMergeRequestRequestReviewAction(scope, reviewFlowVm, avatarIconsProvider)
    val mergeReviewAction = GitLabMergeRequestMergeAction(scope, reviewFlowVm)
    val closeReviewAction = GitLabMergeRequestCloseAction(scope, reviewFlowVm)

    val setMyselfAsReviewerButton = JButton(GitLabMergeRequestSetMyselfAsReviewerAction(scope, reviewFlowVm)).apply {
      isOpaque = false
    }

    val actionGroup = DefaultActionGroup(GitLabBundle.message("merge.request.details.action.review.more.text"), true).apply {
      add(requestReviewAction.toAnAction())
      add(mergeReviewAction.toAnAction())
      add(closeReviewAction.toAnAction())
    }

    val moreActionsButton = createMoreButton(actionGroup)

    return HorizontalListPanel(BUTTONS_GAP).apply {
      add(setMyselfAsReviewerButton)
      add(moreActionsButton)
    }
  }

  private fun createMoreButton(actionGroup: ActionGroup): JComponent {
    return InlineIconButton(AllIcons.Actions.More).apply {
      withBackgroundHover = true
      actionListener = ActionListener { event ->
        val parentComponent = event.source as JComponent
        val popupMenu = ActionManager.getInstance().createActionPopupMenu("GitLab.Review.Details", actionGroup)
        val point = RelativePoint.getSouthWestOf(parentComponent).originalPoint
        popupMenu.component.show(parentComponent, point.x, point.y + JBUIScale.scale(8))
      }
    }
  }
}