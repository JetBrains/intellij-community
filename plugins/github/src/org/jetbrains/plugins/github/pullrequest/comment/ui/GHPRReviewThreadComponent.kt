// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.comment.ui

import com.intellij.collaboration.async.CompletableFutureUtil.handleOnEdt
import com.intellij.collaboration.async.CompletableFutureUtil.successOnEdt
import com.intellij.collaboration.messages.CollaborationToolsBundle
import com.intellij.collaboration.ui.HorizontalListPanel
import com.intellij.collaboration.ui.SingleValueModel
import com.intellij.collaboration.ui.VerticalListPanel
import com.intellij.collaboration.ui.codereview.CodeReviewChatItemUIUtil
import com.intellij.collaboration.ui.codereview.CodeReviewTimelineUIUtil.Thread.Replies.ActionsFolded
import com.intellij.collaboration.ui.codereview.ToggleableContainer
import com.intellij.collaboration.ui.codereview.comment.CommentInputActionsComponentFactory
import com.intellij.collaboration.ui.codereview.timeline.comment.CommentTextFieldFactory
import com.intellij.collaboration.ui.codereview.timeline.thread.TimelineThreadCommentsPanel
import com.intellij.collaboration.ui.html.AsyncHtmlImageLoader
import com.intellij.collaboration.ui.util.swingAction
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.ui.components.labels.LinkLabel
import com.intellij.util.ui.JBUI
import kotlinx.coroutines.flow.MutableStateFlow
import org.jetbrains.plugins.github.api.data.GHUser
import org.jetbrains.plugins.github.i18n.GithubBundle
import org.jetbrains.plugins.github.pullrequest.data.provider.GHPRReviewDataProvider
import org.jetbrains.plugins.github.pullrequest.ui.changes.GHPRSuggestedChangeHelper
import org.jetbrains.plugins.github.ui.avatars.GHAvatarIconsProvider
import java.awt.event.ActionEvent
import javax.swing.AbstractAction
import javax.swing.Action
import javax.swing.JComponent

object GHPRReviewThreadComponent {

  val INLAY_COMPONENT_TYPE = CodeReviewChatItemUIUtil.ComponentType.COMPACT

  fun createForInlay(project: Project,
                     thread: GHPRReviewThreadModel,
                     reviewDataProvider: GHPRReviewDataProvider,
                     htmlImageLoader: AsyncHtmlImageLoader,
                     avatarIconsProvider: GHAvatarIconsProvider,
                     suggestedChangeHelper: GHPRSuggestedChangeHelper,
                     ghostUser: GHUser,
                     currentUser: GHUser): JComponent {
    val panel = VerticalListPanel()
    val commentComponentFactory = GHPRReviewCommentComponent.factory(project, thread, ghostUser,
                                                                     reviewDataProvider,
                                                                     htmlImageLoader,
                                                                     avatarIconsProvider,
                                                                     suggestedChangeHelper,
                                                                     INLAY_COMPONENT_TYPE)
    val commentsPanel = TimelineThreadCommentsPanel(thread, commentComponentFactory, 0, INLAY_COMPONENT_TYPE.fullLeftShift)
    panel.add(commentsPanel)

    if (reviewDataProvider.canComment()) {
      panel.add(getThreadActionsComponent(project, reviewDataProvider, thread, avatarIconsProvider, currentUser).apply {
        border = JBUI.Borders.empty(INLAY_COMPONENT_TYPE.inputPaddingInsets)
      })
    }
    return panel
  }

  private fun getThreadActionsComponent(
    project: Project,
    reviewDataProvider: GHPRReviewDataProvider,
    thread: GHPRReviewThreadModel,
    avatarIconsProvider: GHAvatarIconsProvider,
    currentUser: GHUser
  ): JComponent {
    val toggleModel = SingleValueModel(false)

    return ToggleableContainer.create(
      toggleModel,
      {
        createCollapsedThreadActionsComponent(reviewDataProvider, thread) {
          toggleModel.value = true
        }
      },
      {
        createUncollapsedThreadActionsComponent(project, reviewDataProvider, thread, avatarIconsProvider, currentUser) {
          toggleModel.value = false
        }
      }
    )
  }

  private fun createCollapsedThreadActionsComponent(reviewDataProvider: GHPRReviewDataProvider,
                                                    thread: GHPRReviewThreadModel,
                                                    onReply: () -> Unit): JComponent {

    val toggleReplyLink = LinkLabel<Any>(CollaborationToolsBundle.message("review.comments.reply.action"), null) { _, _ ->
      onReply()
    }.apply {
      isFocusable = true
    }

    val unResolveLink = createUnResolveLink(reviewDataProvider, thread)

    return HorizontalListPanel(ActionsFolded.HORIZONTAL_GAP).apply {
      border = JBUI.Borders.emptyLeft(INLAY_COMPONENT_TYPE.contentLeftShift)

      add(toggleReplyLink)
      add(unResolveLink)
    }
  }

  private fun createUncollapsedThreadActionsComponent(project: Project, reviewDataProvider: GHPRReviewDataProvider,
                                                      thread: GHPRReviewThreadModel,
                                                      avatarIconsProvider: GHAvatarIconsProvider,
                                                      currentUser: GHUser,
                                                      onDone: () -> Unit): JComponent {
    val textFieldModel = GHCommentTextFieldModel(project) { text ->
      reviewDataProvider.addComment(EmptyProgressIndicator(), thread.getElementAt(0).id, text).successOnEdt {
        thread.addComment(it)
        onDone()
      }
    }

    val submitShortcutText = CommentInputActionsComponentFactory.submitShortcutText

    val unResolveAction = object : AbstractAction() {
      override fun actionPerformed(e: ActionEvent?) {
        textFieldModel.isBusyValue.value = true
        if (thread.isResolved) {
          reviewDataProvider.unresolveThread(EmptyProgressIndicator(), thread.id)
        }
        else {
          reviewDataProvider.resolveThread(EmptyProgressIndicator(), thread.id)
        }.handleOnEdt { _, _ ->
          textFieldModel.isBusyValue.value = false
        }
      }
    }

    thread.addAndInvokeStateChangeListener {
      val name = if (thread.isResolved) {
        CollaborationToolsBundle.message("review.comments.unresolve.action")
      }
      else {
        CollaborationToolsBundle.message("review.comments.resolve.action")
      }
      unResolveAction.putValue(Action.NAME, name)
    }

    textFieldModel.isBusyValue.addAndInvokeListener {
      unResolveAction.isEnabled = !it
    }

    val cancelAction = swingAction("") {
      onDone()
    }
    val actions = CommentInputActionsComponentFactory.Config(
      primaryAction = MutableStateFlow(textFieldModel.submitAction(CollaborationToolsBundle.message("review.comments.reply.action"))),
      cancelAction = MutableStateFlow(cancelAction),
      additionalActions = MutableStateFlow(listOf(unResolveAction)),
      submitHint = MutableStateFlow(GithubBundle.message("pull.request.review.thread.reply.hint", submitShortcutText))
    )
    val icon = CommentTextFieldFactory.IconConfig.of(
      CodeReviewChatItemUIUtil.ComponentType.COMPACT, avatarIconsProvider, currentUser.avatarUrl
    )

    return GHCommentTextFieldFactory(textFieldModel).create(actions, icon)
  }

  private fun createUnResolveLink(reviewDataProvider: GHPRReviewDataProvider, thread: GHPRReviewThreadModel): LinkLabel<Any>? {
    if (!reviewDataProvider.canComment()) return null
    val unResolveLink = LinkLabel<Any>("", null) { comp, _ ->
      comp.isEnabled = false
      if (thread.isResolved) {
        reviewDataProvider.unresolveThread(EmptyProgressIndicator(), thread.id)
      }
      else {
        reviewDataProvider.resolveThread(EmptyProgressIndicator(), thread.id)
      }.handleOnEdt { _, _ ->
        comp.isEnabled = true
      }
    }.apply {
      isFocusable = true
    }

    thread.addAndInvokeStateChangeListener {
      unResolveLink.text = if (thread.isResolved) {
        CollaborationToolsBundle.message("review.comments.unresolve.action")
      }
      else {
        CollaborationToolsBundle.message("review.comments.resolve.action")
      }
    }
    return unResolveLink
  }
}
