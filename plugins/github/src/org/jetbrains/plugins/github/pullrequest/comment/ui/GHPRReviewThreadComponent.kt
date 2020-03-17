// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.comment.ui

import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.ui.components.labels.LinkLabel
import com.intellij.ui.components.panels.NonOpaquePanel
import com.intellij.ui.components.panels.VerticalLayout
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UI
import org.jetbrains.plugins.github.api.data.GHUser
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestReviewCommentState
import org.jetbrains.plugins.github.pullrequest.avatars.GHAvatarIconsProvider
import org.jetbrains.plugins.github.pullrequest.data.GHPRReviewDataProvider
import org.jetbrains.plugins.github.pullrequest.ui.timeline.GHPRReviewThreadDiffComponentFactory
import org.jetbrains.plugins.github.ui.util.SingleValueModel
import org.jetbrains.plugins.github.util.handleOnEdt
import org.jetbrains.plugins.github.util.successOnEdt
import java.awt.FlowLayout
import javax.swing.JComponent
import javax.swing.JPanel

object GHPRReviewThreadComponent {

  fun create(thread: GHPRReviewThreadModel, reviewDataProvider: GHPRReviewDataProvider,
             avatarIconsProvider: GHAvatarIconsProvider, currentUser: GHUser): JComponent =
    create(thread, reviewDataProvider, null, avatarIconsProvider, currentUser)

  fun createWithDiff(thread: GHPRReviewThreadModel, reviewDataProvider: GHPRReviewDataProvider,
                     diffComponentFactory: GHPRReviewThreadDiffComponentFactory,
                     avatarIconsProvider: GHAvatarIconsProvider, currentUser: GHUser): JComponent =
    create(thread, reviewDataProvider, diffComponentFactory, avatarIconsProvider, currentUser)

  private fun create(thread: GHPRReviewThreadModel, reviewDataProvider: GHPRReviewDataProvider,
                     diffComponentFactory: GHPRReviewThreadDiffComponentFactory?,
                     avatarIconsProvider: GHAvatarIconsProvider, currentUser: GHUser): JComponent {

    val panel = JPanel(VerticalLayout(12)).apply {
      isOpaque = false
    }
    if (diffComponentFactory != null) {
      panel.add(diffComponentFactory.createComponent(thread.filePath, thread.diffHunk))
    }

    panel.add(
      GHPRReviewThreadCommentsPanel.create(thread, GHPRReviewCommentComponent.factory(thread, reviewDataProvider, avatarIconsProvider)))

    var pending = thread.state == GHPullRequestReviewCommentState.PENDING
    if (!pending) getThreadActionsComponent(reviewDataProvider, thread, avatarIconsProvider, currentUser)?.let { panel.add(it) }
    thread.addStateChangeListener {
      if (pending && thread.state != GHPullRequestReviewCommentState.PENDING) {
        getThreadActionsComponent(reviewDataProvider, thread, avatarIconsProvider, currentUser)?.let { panel.add(it) }
      }
      pending = thread.state == GHPullRequestReviewCommentState.PENDING
    }

    return panel
  }

  private fun getThreadActionsComponent(reviewDataProvider: GHPRReviewDataProvider,
                                        thread: GHPRReviewThreadModel,
                                        avatarIconsProvider: GHAvatarIconsProvider,
                                        currentUser: GHUser): JComponent? {
    if (reviewDataProvider.canComment()) {
      val toggleModel = SingleValueModel(false)
      val textFieldModel = GHPRSubmittableTextField.Model { text ->
        reviewDataProvider.addComment(EmptyProgressIndicator(), text, thread.firstCommentDatabaseId).successOnEdt {
          thread.addComment(
            GHPRReviewCommentModel(it.nodeId, GHPullRequestReviewCommentState.SUBMITTED, it.createdAt, it.bodyHtml, it.user.login,
                                   it.user.htmlUrl, it.user.avatarUrl,
                                   true, true))
          toggleModel.value = false
        }
      }

      val toggleReplyLink = LinkLabel<Any>("Reply", null) { _, _ ->
        toggleModel.value = true
      }.apply {
        isFocusable = true
      }

      val resolveLink = LinkLabel<Any>("Resolve", null).apply {
        isFocusable = true
      }.also {
        it.setListener({ _, _ ->
                         it.isEnabled = false
                         reviewDataProvider.resolveThread(EmptyProgressIndicator(), thread.id).handleOnEdt { _, _ ->
                           it.isEnabled = true
                         }
                       }, null)
      }

      val unresolveLink = LinkLabel<Any>("Unresolve", null).apply {
        isFocusable = true
      }.also {
        it.setListener({ _, _ ->
                         it.isEnabled = false
                         reviewDataProvider.unresolveThread(EmptyProgressIndicator(), thread.id).handleOnEdt { _, _ ->
                           it.isEnabled = true
                         }
                       }, null)
      }

      return GHPRToggleableContainer.create(toggleModel,
                                            { createThreadActionsComponent(thread, toggleReplyLink, resolveLink, unresolveLink) },
                                            {
                                              GHPRSubmittableTextField.create(textFieldModel, avatarIconsProvider, currentUser, "Reply",
                                                                              onCancel = { toggleModel.value = false })
                                            })
    }
    return null
  }

  private fun createThreadActionsComponent(model: GHPRReviewThreadModel,
                                           toggleReplyLink: LinkLabel<Any>,
                                           resolveLink: LinkLabel<Any>,
                                           unresolveLink: LinkLabel<Any>): JComponent {
    fun update() {
      resolveLink.isVisible = !model.isResolved
      unresolveLink.isVisible = model.isResolved
    }

    model.addStateChangeListener(::update)
    update()

    return NonOpaquePanel(FlowLayout(FlowLayout.LEADING, UI.scale(8), 0)).apply {
      border = JBUI.Borders.empty(6, 28, 6, 0)

      add(toggleReplyLink)
      add(resolveLink)
      add(unresolveLink)
    }
  }
}