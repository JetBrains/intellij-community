// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.comment.ui

import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.ui.components.panels.VerticalLayout
import org.jetbrains.plugins.github.api.data.GHUser
import org.jetbrains.plugins.github.pullrequest.avatars.GHAvatarIconsProvider
import org.jetbrains.plugins.github.pullrequest.data.service.GHPRReviewServiceAdapter
import org.jetbrains.plugins.github.pullrequest.ui.timeline.GHPRReviewThreadDiffComponentFactory
import org.jetbrains.plugins.github.util.successOnEdt
import javax.swing.JComponent
import javax.swing.JPanel

object GHPRReviewThreadComponent {

  fun create(project: Project, thread: GHPRReviewThreadModel, reviewService: GHPRReviewServiceAdapter,
             avatarIconsProvider: GHAvatarIconsProvider, currentUser: GHUser): JComponent =
    create(project, thread, reviewService, null, avatarIconsProvider, currentUser)

  fun createWithDiff(project: Project, thread: GHPRReviewThreadModel, reviewService: GHPRReviewServiceAdapter,
                     diffComponentFactory: GHPRReviewThreadDiffComponentFactory,
                     avatarIconsProvider: GHAvatarIconsProvider, currentUser: GHUser): JComponent =
    create(project, thread, reviewService, diffComponentFactory, avatarIconsProvider, currentUser)

  private fun create(project: Project, thread: GHPRReviewThreadModel, reviewService: GHPRReviewServiceAdapter,
                     diffComponentFactory: GHPRReviewThreadDiffComponentFactory?,
                     avatarIconsProvider: GHAvatarIconsProvider, currentUser: GHUser): JComponent {

    val panel = JPanel(VerticalLayout(12)).apply {
      isOpaque = false
    }
    if (diffComponentFactory != null) {
      panel.add(diffComponentFactory.createComponent(thread.filePath, thread.diffHunk))
    }

    panel.add(GHPRReviewThreadCommentsPanel.create(thread, GHPRReviewCommentComponent.factory(avatarIconsProvider)))

    if (reviewService.canComment()) {
      panel.add(GHPRCommentsUIUtil.createTogglableCommentField(project, avatarIconsProvider, currentUser, "Reply") { text ->
        reviewService.addComment(EmptyProgressIndicator(), text, thread.firstCommentDatabaseId).successOnEdt {
          thread.addComment(GHPRReviewCommentModel(it.nodeId, it.createdAt, it.bodyHtml, it.user.login, it.user.htmlUrl, it.user.avatarUrl))
        }
      })
    }
    return panel
  }
}