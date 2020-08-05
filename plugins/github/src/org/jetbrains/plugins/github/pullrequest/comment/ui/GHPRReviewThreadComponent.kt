// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.comment.ui

import com.intellij.ide.plugins.newui.VerticalLayout
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.ui.components.labels.LinkLabel
import com.intellij.ui.components.panels.HorizontalBox
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UI
import org.jetbrains.plugins.github.api.data.GHUser
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestReviewCommentState
import org.jetbrains.plugins.github.i18n.GithubBundle
import org.jetbrains.plugins.github.pullrequest.avatars.GHAvatarIconsProvider
import org.jetbrains.plugins.github.pullrequest.data.provider.GHPRReviewDataProvider
import org.jetbrains.plugins.github.pullrequest.ui.timeline.GHPRReviewThreadDiffComponentFactory
import org.jetbrains.plugins.github.ui.util.SingleValueModel
import org.jetbrains.plugins.github.util.handleOnEdt
import org.jetbrains.plugins.github.util.successOnEdt
import javax.swing.Box
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

    val panel = JPanel(VerticalLayout(UI.scale(12))).apply {
      isOpaque = false
    }
    if (diffComponentFactory != null) {
      panel.add(diffComponentFactory.createComponent(thread.filePath, thread.diffHunk, thread.commit?.oid), VerticalLayout.FILL_HORIZONTAL)
    }

    panel.add(
      GHPRReviewThreadCommentsPanel.create(thread, GHPRReviewCommentComponent.factory(reviewDataProvider, avatarIconsProvider)),
      VerticalLayout.FILL_HORIZONTAL)

    if (reviewDataProvider.canComment()) {
      panel.add(getThreadActionsComponent(reviewDataProvider, thread, avatarIconsProvider, currentUser), VerticalLayout.FILL_HORIZONTAL)
    }
    return panel
  }

  private fun getThreadActionsComponent(reviewDataProvider: GHPRReviewDataProvider,
                                        thread: GHPRReviewThreadModel,
                                        avatarIconsProvider: GHAvatarIconsProvider,
                                        currentUser: GHUser): JComponent {
    val toggleModel = SingleValueModel(false)
    val textFieldModel = GHSubmittableTextFieldModel { text ->
      reviewDataProvider.addComment(EmptyProgressIndicator(), thread.getElementAt(0).id, text).successOnEdt {
        thread.addComment(GHPRReviewCommentModel.convert(it))
        toggleModel.value = false
      }
    }

    val toggleReplyLink = LinkLabel<Any>(GithubBundle.message("pull.request.review.thread.reply"), null) { _, _ ->
      toggleModel.value = true
    }.apply {
      isFocusable = true
    }

    val resolveLink = LinkLabel<Any>(GithubBundle.message("pull.request.review.thread.resolve"), null).apply {
      isFocusable = true
    }.also {
      it.setListener({ _, _ ->
                       it.isEnabled = false
                       reviewDataProvider.resolveThread(EmptyProgressIndicator(), thread.id).handleOnEdt { _, _ ->
                         it.isEnabled = true
                       }
                     }, null)
    }

    val unresolveLink = LinkLabel<Any>(GithubBundle.message("pull.request.review.thread.unresolve"), null).apply {
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
                                            GHSubmittableTextFieldFactory(textFieldModel).create(avatarIconsProvider, currentUser,
                                                                                                 GithubBundle.message(
                                                                                                   "pull.request.review.thread.reply"),
                                                                                                 onCancel = { toggleModel.value = false })
                                          })
  }

  private fun createThreadActionsComponent(model: GHPRReviewThreadModel,
                                           toggleReplyLink: LinkLabel<Any>,
                                           resolveLink: LinkLabel<Any>,
                                           unresolveLink: LinkLabel<Any>): JComponent {
    fun update() {
      resolveLink.isVisible = model.state != GHPullRequestReviewCommentState.PENDING && !model.isResolved
      unresolveLink.isVisible = model.state != GHPullRequestReviewCommentState.PENDING && model.isResolved
    }

    model.addStateChangeListener(::update)
    update()

    return HorizontalBox().apply {
      isOpaque = false
      border = JBUI.Borders.empty(6, 28, 6, 0)

      add(toggleReplyLink)
      add(Box.createHorizontalStrut(UI.scale(8)))
      add(resolveLink)
      add(unresolveLink)
    }
  }
}