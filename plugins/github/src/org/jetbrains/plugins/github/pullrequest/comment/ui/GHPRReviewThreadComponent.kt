// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.comment.ui

import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.command.executeCommand
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.ui.components.labels.LinkLabel
import com.intellij.ui.components.panels.NonOpaquePanel
import com.intellij.ui.components.panels.VerticalLayout
import com.intellij.util.ui.JBUI
import org.jetbrains.plugins.github.api.data.GHUser
import org.jetbrains.plugins.github.pullrequest.avatars.GHAvatarIconsProvider
import org.jetbrains.plugins.github.pullrequest.data.service.GHPRReviewServiceAdapter
import org.jetbrains.plugins.github.pullrequest.ui.timeline.GHPRReviewThreadDiffComponentFactory
import org.jetbrains.plugins.github.ui.util.SingleValueModel
import org.jetbrains.plugins.github.util.successOnEdt
import java.awt.FlowLayout
import java.util.concurrent.CompletableFuture
import javax.swing.JComponent
import javax.swing.JPanel

object GHPRReviewThreadComponent {

  fun create(thread: GHPRReviewThreadModel, reviewService: GHPRReviewServiceAdapter,
             avatarIconsProvider: GHAvatarIconsProvider, currentUser: GHUser): JComponent =
    create(thread, reviewService, null, avatarIconsProvider, currentUser)

  fun createWithDiff(thread: GHPRReviewThreadModel, reviewService: GHPRReviewServiceAdapter,
                     diffComponentFactory: GHPRReviewThreadDiffComponentFactory,
                     avatarIconsProvider: GHAvatarIconsProvider, currentUser: GHUser): JComponent =
    create(thread, reviewService, diffComponentFactory, avatarIconsProvider, currentUser)

  private fun create(thread: GHPRReviewThreadModel, reviewService: GHPRReviewServiceAdapter,
                     diffComponentFactory: GHPRReviewThreadDiffComponentFactory?,
                     avatarIconsProvider: GHAvatarIconsProvider, currentUser: GHUser): JComponent {

    val panel = JPanel(VerticalLayout(12)).apply {
      isOpaque = false
    }
    if (diffComponentFactory != null) {
      panel.add(diffComponentFactory.createComponent(thread.filePath, thread.diffHunk))
    }

    panel.add(GHPRReviewThreadCommentsPanel.create(thread, GHPRReviewCommentComponent.factory(thread, reviewService, avatarIconsProvider)))

    if (reviewService.canComment()) {
      panel.add(createThreadActionsPanel(avatarIconsProvider, currentUser) { text ->
        reviewService.addComment(EmptyProgressIndicator(), text, thread.firstCommentDatabaseId).successOnEdt {
          thread.addComment(GHPRReviewCommentModel(it.nodeId, it.createdAt, it.bodyHtml, it.user.login, it.user.htmlUrl, it.user.avatarUrl,
                                                   true, true))
        }
      })
    }
    return panel
  }

  private fun createThreadActionsPanel(avatarIconsProvider: GHAvatarIconsProvider, author: GHUser,
                                       submitter: (String) -> CompletableFuture<*>): JComponent {
    var text = ""

    return GHPRTogglableContainer.create(::createThreadActionsComponent) { toggleModel ->
      val model = GHPRSubmittableTextField.Model {
        submitter(it).successOnEdt {
          text = ""
          toggleModel.value = false
        }
      }.apply {
        executeCommand {
          runWriteAction {
            document.setText(text)
          }
        }
      }
      GHPRSubmittableTextField.create(model, avatarIconsProvider, author, "Reply") {
        text = model.document.text
        toggleModel.value = false
      }
    }
  }

  private fun createThreadActionsComponent(toggleModel: SingleValueModel<Boolean>): JComponent {
    val toggleReplyLink = LinkLabel<Any>("Reply", null) { _, _ ->
      toggleModel.value = true
    }.apply {
      isFocusable = true
    }
    return NonOpaquePanel(FlowLayout(FlowLayout.LEADING, 0, 0)).apply {
      border = JBUI.Borders.empty(6, 28, 6, 0)
      add(toggleReplyLink)
    }
  }
}