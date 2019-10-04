// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.comment.ui

import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.ui.IdeBorderFactory
import com.intellij.ui.components.panels.Wrapper
import com.intellij.util.ui.GraphicsUtil
import com.intellij.util.ui.JBInsets
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import org.jetbrains.plugins.github.api.data.GHUser
import org.jetbrains.plugins.github.pullrequest.avatars.CachingGithubAvatarIconsProvider
import org.jetbrains.plugins.github.pullrequest.data.GHPRReviewServiceAdapter
import org.jetbrains.plugins.github.util.GithubUIUtil
import org.jetbrains.plugins.github.util.successOnEdt
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Rectangle
import java.awt.geom.RoundRectangle2D
import javax.swing.JComponent

class GHPREditorReviewThreadComponentFactoryImpl
internal constructor(private val project: Project,
                     private val avatarIconsProviderFactory: CachingGithubAvatarIconsProvider.Factory,
                     private val currentUser: GHUser)
  : GHPREditorReviewThreadComponentFactory {

  override fun createComponent(reviewService: GHPRReviewServiceAdapter, thread: GHPRReviewThreadModel): JComponent {
    val wrapper = RoundedPanel().apply {
      border = IdeBorderFactory.createRoundedBorder(10, 1)
    }
    val avatarIconsProvider = avatarIconsProviderFactory.create(GithubUIUtil.avatarSize, wrapper)

    val replyField = GHPRCommentsUIUtil.createCommentField(project, avatarIconsProvider, currentUser, "Reply") { text ->
      reviewService.addComment(EmptyProgressIndicator(), text, thread.firstCommentDatabaseId).successOnEdt {
        thread.addComment(GHPRReviewCommentModel(it.nodeId, it.createdAt, it.bodyHtml, it.user.login, it.user.htmlUrl, it.user.avatarUrl))
      }
    }.apply {
      border = JBUI.Borders.emptyBottom(10)
    }
    val panel = JBUI.Panels.simplePanel(GHPRReviewThreadCommentsPanel(thread, avatarIconsProvider))
      .addToBottom(replyField)
      .withBorder(JBUI.Borders.empty(0, UIUtil.DEFAULT_HGAP))
      .andTransparent()
    wrapper.setContent(panel)
    return wrapper
  }

  private class RoundedPanel : Wrapper() {
    override fun paintComponent(g: Graphics) {
      GraphicsUtil.setupRoundedBorderAntialiasing(g)

      val g2 = g as Graphics2D
      val rect = Rectangle(size)
      JBInsets.removeFrom(rect, insets)
      g2.color = background
      g2.fill(RoundRectangle2D.Float(rect.x.toFloat(), rect.y.toFloat(), rect.width.toFloat(), rect.height.toFloat(), 10f, 10f))
    }
  }
}