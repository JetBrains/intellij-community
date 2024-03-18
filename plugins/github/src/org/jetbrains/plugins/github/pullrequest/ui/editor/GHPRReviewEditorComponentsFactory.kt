// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.ui.editor

import com.intellij.CommonBundle
import com.intellij.collaboration.async.inverted
import com.intellij.collaboration.async.mapScoped
import com.intellij.collaboration.async.mapState
import com.intellij.collaboration.async.stateInNow
import com.intellij.collaboration.messages.CollaborationToolsBundle
import com.intellij.collaboration.ui.ComponentListPanelFactory
import com.intellij.collaboration.ui.HorizontalListPanel
import com.intellij.collaboration.ui.VerticalListPanel
import com.intellij.collaboration.ui.codereview.CodeReviewChatItemUIUtil
import com.intellij.collaboration.ui.codereview.CodeReviewTimelineUIUtil
import com.intellij.collaboration.ui.codereview.comment.CodeReviewCommentTextFieldFactory
import com.intellij.collaboration.ui.codereview.comment.CodeReviewCommentUIUtil
import com.intellij.collaboration.ui.codereview.comment.CommentInputActionsComponentFactory
import com.intellij.collaboration.ui.codereview.comment.submitActionIn
import com.intellij.collaboration.ui.codereview.timeline.comment.CommentTextFieldFactory
import com.intellij.collaboration.ui.codereview.timeline.thread.CodeReviewResolvableItemViewModel
import com.intellij.collaboration.ui.codereview.timeline.thread.TimelineThreadCommentsPanel
import com.intellij.collaboration.ui.util.*
import com.intellij.openapi.ui.MessageDialogBuilder
import com.intellij.ui.components.ActionLink
import com.intellij.util.ui.JBUI
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import org.jetbrains.plugins.github.i18n.GithubBundle
import org.jetbrains.plugins.github.pullrequest.ui.comment.GHPRCompactReviewThreadViewModel
import org.jetbrains.plugins.github.pullrequest.ui.comment.GHPRCompactReviewThreadViewModel.CommentItem
import org.jetbrains.plugins.github.pullrequest.ui.comment.GHPRReviewThreadCommentComponentFactory
import org.jetbrains.plugins.github.pullrequest.ui.comment.GHPRReviewThreadComponentFactory
import javax.swing.AbstractAction
import javax.swing.Action
import javax.swing.JComponent

internal object GHPRReviewEditorComponentsFactory {
  fun createThreadIn(cs: CoroutineScope, vm: GHPRCompactReviewThreadViewModel): JComponent {
    val commentsPanel = ComponentListPanelFactory.createVertical(cs, vm.comments) { item ->
      val itemCs = this
      when (item) {
        is CommentItem.Expander -> TimelineThreadCommentsPanel.createUnfoldComponent(item.collapsedCount) {
          item.expand()
        }.apply {
          border = JBUI.Borders.empty(TimelineThreadCommentsPanel.UNFOLD_BUTTON_VERTICAL_GAP,
                                      CodeReviewChatItemUIUtil.ComponentType.COMPACT.fullLeftShift,
                                      TimelineThreadCommentsPanel.UNFOLD_BUTTON_VERTICAL_GAP,
                                      0)
        }
        is CommentItem.Comment ->
          GHPRReviewThreadCommentComponentFactory.createIn(itemCs, item.vm, CodeReviewChatItemUIUtil.ComponentType.COMPACT)
      }
    }

    return VerticalListPanel().apply {
      name = "GitHub Thread in Editor Panel ${vm.id}"
      border = JBUI.Borders.empty(CodeReviewCommentUIUtil.getInlayPadding(CodeReviewChatItemUIUtil.ComponentType.COMPACT))
      add(commentsPanel)

      bindChildIn(cs, vm.isWritingReply) { isWriting ->
        if (isWriting) {
          GHPRReviewThreadComponentFactory.createThreadReplyComponentIn(cs, vm, CodeReviewChatItemUIUtil.ComponentType.COMPACT)
        }
        else {
          createReplyActionsPanel(vm).apply {
            border = JBUI.Borders.empty(8, CodeReviewChatItemUIUtil.ComponentType.COMPACT.fullLeftShift)
          }
        }
      }
    }.let {
      CodeReviewCommentUIUtil.createEditorInlayPanel(it)
    }
  }

  private fun CoroutineScope.createReplyActionsPanel(vm: GHPRCompactReviewThreadViewModel): JComponent {
    val cs = this
    val replyLink = ActionLink(CollaborationToolsBundle.message("review.comments.reply.action")) {
      vm.startWritingReply()
    }.apply {
      isFocusPainted = false
    }

    val resolveLink = ActionLink(createResolveAction(vm)).apply {
      autoHideOnDisable = false
      bindVisibilityIn(cs, vm.canChangeResolvedState)
    }

    return HorizontalListPanel(CodeReviewTimelineUIUtil.Thread.Replies.ActionsFolded.HORIZONTAL_GROUP_GAP).apply {
      add(replyLink)
      add(resolveLink)
    }
  }

  private fun CoroutineScope.createResolveAction(resolveVm: CodeReviewResolvableItemViewModel): AbstractAction {
    val cs = this
    return swingAction(CollaborationToolsBundle.message("review.comments.resolve.action")) {
      resolveVm.changeResolvedState()
    }.apply {
      bindEnabledIn(cs, resolveVm.isBusy.inverted())
      bindTextIn(cs, resolveVm.isResolved.map(CodeReviewCommentUIUtil::getResolveToggleActionText))
    }
  }

  fun createNewCommentIn(cs: CoroutineScope, vm: GHPRReviewNewCommentEditorViewModel): JComponent {
    val primaryAction = vm.submitActions.mapScoped {
      createUiAction(vm, it.firstOrNull())
    }.stateInNow(cs, null)

    val secondaryActions = vm.submitActions.mapScoped { actions ->
      actions.drop(1).map { createUiAction(vm, it) }
    }.stateInNow(cs, emptyList())

    val cancelAction = swingAction("") {
      if (vm.text.value.isBlank()) {
        vm.cancel()
      }
      else if (MessageDialogBuilder.yesNo(CollaborationToolsBundle.message("review.comments.discard.new.confirmation.title"),
                                          CollaborationToolsBundle.message("review.comments.discard.new.confirmation")).ask(vm.project)) {
        vm.cancel()
      }
    }

    val submitShortcutText = CommentInputActionsComponentFactory.submitShortcutText
    val hint = vm.submitActions.mapState {
      when (it.firstOrNull()) {
        is GHPRReviewNewCommentEditorViewModel.SubmitAction.CreateReview ->
          CollaborationToolsBundle.message("review.comment.hint", submitShortcutText)
        is GHPRReviewNewCommentEditorViewModel.SubmitAction.CreateReviewComment ->
          CollaborationToolsBundle.message("review.comment.hint", submitShortcutText)
        is GHPRReviewNewCommentEditorViewModel.SubmitAction.CreateSingleComment ->
          CollaborationToolsBundle.message("review.comment.hint", submitShortcutText)
        null -> ""
      }
    }

    val actions = CommentInputActionsComponentFactory.Config(
      primaryAction = primaryAction,
      secondaryActions = secondaryActions,
      cancelAction = MutableStateFlow(cancelAction),
      submitHint = hint
    )

    val itemType = CodeReviewChatItemUIUtil.ComponentType.COMPACT
    val icon = CommentTextFieldFactory.IconConfig.of(itemType, vm.avatarIconsProvider, vm.currentUser.avatarUrl)

    val editor = CodeReviewCommentTextFieldFactory.createIn(cs, vm, actions, icon).apply {
      border = JBUI.Borders.empty(itemType.inputPaddingInsets)
    }

    return CodeReviewCommentUIUtil.createEditorInlayPanel(editor)
  }

  private fun CoroutineScope.createUiAction(vm: GHPRReviewNewCommentEditorViewModel,
                                            action: GHPRReviewNewCommentEditorViewModel.SubmitAction?): Action {
    val cs = this
    return when (action) {
      is GHPRReviewNewCommentEditorViewModel.SubmitAction.CreateReview ->
        vm.submitActionIn(cs, GithubBundle.message("pull.request.review.editor.start.review")) { action() }
      is GHPRReviewNewCommentEditorViewModel.SubmitAction.CreateReviewComment ->
        vm.submitActionIn(cs, GithubBundle.message("pull.request.review.editor.add.review.comment")) { action() }
      is GHPRReviewNewCommentEditorViewModel.SubmitAction.CreateSingleComment ->
        vm.submitActionIn(cs, GithubBundle.message("pull.request.review.editor.add.single.comment")) { action() }
      null -> swingAction(CommonBundle.getLoadingTreeNodeText()) {}.apply {
        isEnabled = false
      }
    }
  }
}