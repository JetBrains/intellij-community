// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.ui.comment

import com.intellij.collaboration.async.inverted
import com.intellij.collaboration.async.mapScoped
import com.intellij.collaboration.messages.CollaborationToolsBundle
import com.intellij.collaboration.ui.CollaborationToolsUIUtil
import com.intellij.collaboration.ui.codereview.CodeReviewChatItemUIUtil
import com.intellij.collaboration.ui.codereview.comment.CodeReviewCommentTextFieldFactory
import com.intellij.collaboration.ui.codereview.comment.CodeReviewCommentUIUtil
import com.intellij.collaboration.ui.codereview.comment.CommentInputActionsComponentFactory
import com.intellij.collaboration.ui.codereview.comment.submitActionIn
import com.intellij.collaboration.ui.codereview.timeline.comment.CommentTextFieldFactory
import com.intellij.collaboration.ui.util.bindEnabledIn
import com.intellij.collaboration.ui.util.bindTextIn
import com.intellij.collaboration.ui.util.bindVisibilityIn
import com.intellij.collaboration.ui.util.swingAction
import com.intellij.util.ui.JBUI
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.swing.JComponent

internal object GHPRReviewThreadComponentFactory {
  fun createThreadReplyComponentIn(cs: CoroutineScope,
                                   vm: GHPRReviewThreadViewModel,
                                   componentType: CodeReviewChatItemUIUtil.ComponentType): JComponent {
    val additionalActions = vm.canChangeResolvedState.mapScoped {
      val currentCs = this
      buildList {
        if (it) {
          add(swingAction(CollaborationToolsBundle.message("review.comments.resolve.action")) {
            vm.changeResolvedState()
          }.apply {
            bindEnabledIn(currentCs, vm.isBusy.inverted())
            bindTextIn(currentCs, vm.isResolved.map { CodeReviewCommentUIUtil.getResolveToggleActionText(it) })
          })
        }
      }
    }.stateIn(cs, SharingStarted.Eagerly, emptyList())

    val replyVm = vm.newReplyVm
    val actions = CommentInputActionsComponentFactory.Config(
      primaryAction = MutableStateFlow(replyVm.submitActionIn(cs, CollaborationToolsBundle.message("review.comments.reply.action")) {
        replyVm.submit()
      }),
      additionalActions = additionalActions,
      cancelAction = MutableStateFlow(null),
      submitHint = MutableStateFlow(CollaborationToolsBundle.message("review.comments.reply.hint",
                                                                     CommentInputActionsComponentFactory.submitShortcutText))
    )

    val icon = CommentTextFieldFactory.IconConfig.of(componentType, vm.avatarIconsProvider, replyVm.currentUser.avatarUrl)

    val replyComponent = CodeReviewCommentTextFieldFactory.createIn(cs, replyVm, actions, icon).let {
      CollaborationToolsUIUtil
        .wrapWithLimitedSize(it, maxWidth = CodeReviewChatItemUIUtil.TEXT_CONTENT_WIDTH + componentType.contentLeftShift)
    }.apply {
      border = JBUI.Borders.empty(componentType.inputPaddingInsets)
      //TODO: show resolve when available separately from reply
      bindVisibilityIn(cs, vm.canCreateReplies)
    }
    return replyComponent
  }
}