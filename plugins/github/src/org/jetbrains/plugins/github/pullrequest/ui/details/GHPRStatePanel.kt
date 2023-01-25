// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.ui.details

import com.intellij.collaboration.messages.CollaborationToolsBundle
import com.intellij.collaboration.ui.util.bindText
import com.intellij.collaboration.ui.util.bindVisibility
import com.intellij.collaboration.ui.util.emptyBorders
import com.intellij.collaboration.ui.util.toAnAction
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.CustomComponentAction
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.application.EDT
import com.intellij.ui.CardLayoutPanel
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.components.JBOptionButton
import com.intellij.ui.components.panels.HorizontalLayout
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.childScope
import com.intellij.util.ui.InlineIconButton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import net.miginfocom.layout.CC
import net.miginfocom.layout.LC
import net.miginfocom.swing.MigLayout
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestState
import org.jetbrains.plugins.github.i18n.GithubBundle
import org.jetbrains.plugins.github.pullrequest.action.GHPRActionKeys
import org.jetbrains.plugins.github.pullrequest.action.GHPRReviewSubmitAction
import org.jetbrains.plugins.github.pullrequest.data.provider.GHPRDataProvider
import org.jetbrains.plugins.github.pullrequest.data.service.GHPRSecurityService
import org.jetbrains.plugins.github.pullrequest.ui.details.action.*
import org.jetbrains.plugins.github.pullrequest.ui.details.model.GHPRDetailsViewModel
import org.jetbrains.plugins.github.pullrequest.ui.details.model.GHPRMetadataModel
import org.jetbrains.plugins.github.pullrequest.ui.details.model.GHPRReviewFlowViewModel
import org.jetbrains.plugins.github.pullrequest.ui.details.model.GHPRStateModel
import org.jetbrains.plugins.github.ui.avatars.GHAvatarIconsProvider
import java.awt.event.ActionListener
import javax.swing.Action
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel

internal class GHPRStatePanel(
  parentScope: CoroutineScope,
  private val reviewDetailsVm: GHPRDetailsViewModel,
  private val reviewFlowVm: GHPRReviewFlowViewModel,
  private val dataProvider: GHPRDataProvider,
  private val securityService: GHPRSecurityService,
  private val stateModel: GHPRStateModel,
  private val metadataModel: GHPRMetadataModel,
  private val avatarIconsProvider: GHAvatarIconsProvider
) : CardLayoutPanel<GHPRReviewFlowViewModel.ReviewRole, GHPRStatePanel.StateUI, JComponent>() {
  private val scope = parentScope.childScope(Dispatchers.EDT)

  init {
    scope.launch {
      reviewFlowVm.roleState.collect { role ->
        select(role, true)
      }
    }
  }

  override fun prepare(key: GHPRReviewFlowViewModel.ReviewRole): StateUI {
    return when (key) {
      GHPRReviewFlowViewModel.ReviewRole.AUTHOR -> StateUI.Author(
        scope, reviewDetailsVm, reviewFlowVm, stateModel, securityService, metadataModel, avatarIconsProvider
      )
      GHPRReviewFlowViewModel.ReviewRole.REVIEWER -> StateUI.Reviewer(
        scope, reviewDetailsVm, reviewFlowVm, stateModel, securityService, metadataModel, avatarIconsProvider, dataProvider
      )
      GHPRReviewFlowViewModel.ReviewRole.GUEST -> StateUI.Guest(
        scope, reviewDetailsVm, reviewFlowVm, stateModel, securityService, metadataModel, avatarIconsProvider
      )
    }
  }

  override fun create(ui: StateUI) = ui.createComponent()

  internal sealed class StateUI(
    protected val scope: CoroutineScope,
    private val reviewDetailsVm: GHPRDetailsViewModel,
    protected val reviewFlowVm: GHPRReviewFlowViewModel,
    protected val metadataModel: GHPRMetadataModel,
    protected val stateModel: GHPRStateModel,
    protected val securityService: GHPRSecurityService,
    protected val avatarIconsProvider: GHAvatarIconsProvider
  ) {
    protected val horizontalLayout = MigLayout(
      LC()
        .emptyBorders()
        .fill()
        .flowX()
        .hideMode(3)
    )

    abstract fun createReviewActionsForOpenReview(): JComponent

    fun createComponent(): JComponent {
      val reviewActionsComponentForOpenReview = createReviewActionsForOpenReview().apply {
        bindVisibility(scope, reviewDetailsVm.reviewMergeState.map { it == GHPullRequestState.OPEN })
      }
      val reviewActionsComponentForCloseReview = JButton().apply {
        isOpaque = false
        text = GithubBundle.message("pull.request.reopen.action")
        action = GHPRReopenAction(stateModel, securityService)
        bindVisibility(scope, reviewDetailsVm.reviewMergeState.map { it == GHPullRequestState.CLOSED })
      }

      return JPanel(HorizontalLayout(0)).apply {
        isOpaque = false
        add(reviewActionsComponentForOpenReview, HorizontalLayout.LEFT)
        add(reviewActionsComponentForCloseReview, HorizontalLayout.LEFT)
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
        GHPRRebaseMergeAction(stateModel, securityService),
        GHPRSquashMergeAction(stateModel, securityService)
      )
      return JBOptionButton(GHPRCommitMergeAction(stateModel, securityService), actions).apply {
        bindVisibility(scope, reviewFlowVm.reviewState.map { it == GHPRReviewFlowViewModel.ReviewState.ACCEPTED })
      }
    }

    class Author(
      scope: CoroutineScope,
      reviewDetailsVm: GHPRDetailsViewModel,
      reviewFlowVm: GHPRReviewFlowViewModel,
      stateModel: GHPRStateModel,
      securityService: GHPRSecurityService,
      metadataModel: GHPRMetadataModel,
      avatarIconsProvider: GHAvatarIconsProvider
    ) : StateUI(scope, reviewDetailsVm, reviewFlowVm, metadataModel, stateModel, securityService, avatarIconsProvider) {
      override fun createReviewActionsForOpenReview(): JComponent {
        val requestReviewButton = JButton().apply {
          isOpaque = false
          text = CollaborationToolsBundle.message("review.details.action.request")
          action = GHPRRequestReviewAction(stateModel, securityService, metadataModel, avatarIconsProvider)
          bindVisibility(scope, combine(reviewFlowVm.reviewState, reviewFlowVm.requestedReviewersState) { reviewState, requestedReviewers ->
            reviewState == GHPRReviewFlowViewModel.ReviewState.NEED_REVIEW ||
            (reviewState == GHPRReviewFlowViewModel.ReviewState.WAIT_FOR_UPDATES && requestedReviewers.isNotEmpty())
          })
        }
        val reRequestReviewButton = JButton().apply {
          isOpaque = false
          text = CollaborationToolsBundle.message("review.details.action.rerequest")
          action = GHPRReRequestReviewAction(stateModel, securityService, metadataModel, reviewFlowVm)
          bindVisibility(scope, combine(reviewFlowVm.reviewState, reviewFlowVm.requestedReviewersState) { reviewState, requestedReviewers ->
            reviewState == GHPRReviewFlowViewModel.ReviewState.WAIT_FOR_UPDATES && requestedReviewers.isEmpty()
          })
        }
        val mergePullRequest = createMergeReviewButton(scope, reviewFlowVm)

        val actionGroup = DefaultActionGroup(GithubBundle.message("pull.request.review.actions.more.name"), true)
        val moreActions = createMoreButton(actionGroup)

        scope.launch {
          reviewFlowVm.reviewState.collect { reviewState ->
            actionGroup.removeAll()
            when (reviewState) {
              GHPRReviewFlowViewModel.ReviewState.NEED_REVIEW, GHPRReviewFlowViewModel.ReviewState.WAIT_FOR_UPDATES -> {
                actionGroup.add(GHPRCommitMergeAction(stateModel, securityService).toAnAction())
                actionGroup.add(GHPRCloseAction(stateModel, securityService).toAnAction())
              }
              GHPRReviewFlowViewModel.ReviewState.ACCEPTED -> {
                actionGroup.add(GHPRRequestReviewAction(stateModel, securityService, metadataModel, avatarIconsProvider).toAnAction())
                actionGroup.add(GHPRCloseAction(stateModel, securityService).toAnAction())
              }
            }
          }
        }

        return JPanel(horizontalLayout).apply {
          isOpaque = false
          add(requestReviewButton, CC())
          add(reRequestReviewButton, CC())
          add(mergePullRequest, CC())
          add(moreActions, CC())
        }
      }
    }

    class Reviewer(
      scope: CoroutineScope,
      reviewDetailsVm: GHPRDetailsViewModel,
      reviewFlowVm: GHPRReviewFlowViewModel,
      stateModel: GHPRStateModel,
      securityService: GHPRSecurityService,
      metadataModel: GHPRMetadataModel,
      avatarIconsProvider: GHAvatarIconsProvider,
      private val dataProvider: GHPRDataProvider
    ) : StateUI(scope, reviewDetailsVm, reviewFlowVm, metadataModel, stateModel, securityService, avatarIconsProvider) {
      override fun createReviewActionsForOpenReview(): JComponent {
        val submitReviewButton = createSubmitReviewButton(scope, reviewFlowVm, dataProvider)
        val mergeReviewButton = createMergeReviewButton(scope, reviewFlowVm)

        val actionGroup = DefaultActionGroup(GithubBundle.message("pull.request.review.actions.more.name"), true)
        val moreActions = createMoreButton(actionGroup)

        scope.launch {
          reviewFlowVm.reviewState.collect { reviewState ->
            actionGroup.removeAll()
            when (reviewState) {
              GHPRReviewFlowViewModel.ReviewState.NEED_REVIEW, GHPRReviewFlowViewModel.ReviewState.WAIT_FOR_UPDATES -> {
                actionGroup.add(GHPRRequestReviewAction(stateModel, securityService, metadataModel, avatarIconsProvider).toAnAction())
                actionGroup.add(GHPRCommitMergeAction(stateModel, securityService).toAnAction())
                actionGroup.add(GHPRCloseAction(stateModel, securityService).toAnAction())
              }
              GHPRReviewFlowViewModel.ReviewState.ACCEPTED -> {
                actionGroup.add(GHPRRequestReviewAction(stateModel, securityService, metadataModel, avatarIconsProvider).toAnAction())
                actionGroup.add(GHPRCloseAction(stateModel, securityService).toAnAction())
              }
            }
          }
        }

        return JPanel(horizontalLayout).apply {
          isOpaque = false
          add(submitReviewButton, CC())
          add(mergeReviewButton, CC())
          add(moreActions, CC())
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

          bindText(scope, reviewFlowVm.pendingCommentsState.map { pendingComments ->
            GithubBundle.message("pull.request.review.actions.submit", pendingComments)
          })
          bindVisibility(scope, reviewFlowVm.reviewState.map {
            it == GHPRReviewFlowViewModel.ReviewState.WAIT_FOR_UPDATES || it == GHPRReviewFlowViewModel.ReviewState.NEED_REVIEW
          })
        }
      }
    }

    class Guest(
      scope: CoroutineScope,
      reviewDetailsVm: GHPRDetailsViewModel,
      reviewFlowVm: GHPRReviewFlowViewModel,
      stateModel: GHPRStateModel,
      securityService: GHPRSecurityService,
      metadataModel: GHPRMetadataModel,
      avatarIconsProvider: GHAvatarIconsProvider
    ) : StateUI(scope, reviewDetailsVm, reviewFlowVm, metadataModel, stateModel, securityService, avatarIconsProvider) {
      override fun createReviewActionsForOpenReview(): JComponent {
        val setAsReviewerButton = JButton().apply {
          isOpaque = false
          text = CollaborationToolsBundle.message("review.details.action.set.myself.as.reviewer")
          action = GHPRSetMyselfAsReviewerAction(stateModel, securityService, metadataModel)
        }
        val actionGroup = DefaultActionGroup(GithubBundle.message("pull.request.review.actions.more.name"), true).apply {
          add(GHPRRequestReviewAction(stateModel, securityService, metadataModel, avatarIconsProvider).toAnAction())
          add(GHPRCommitMergeAction(stateModel, securityService).toAnAction())
          add(GHPRCloseAction(stateModel, securityService).toAnAction())
        }
        val moreButton = createMoreButton(actionGroup)

        return JPanel(horizontalLayout).apply {
          isOpaque = false
          add(setAsReviewerButton, CC())
          add(moreButton, CC())
        }
      }
    }
  }
}