// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.comment.ui

import com.intellij.openapi.editor.colors.EditorColors
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.ui.components.panels.Wrapper
import com.intellij.util.ui.GraphicsUtil
import com.intellij.util.ui.JBInsets
import com.intellij.util.ui.JBUI
import org.jetbrains.plugins.github.api.data.GHUser
import org.jetbrains.plugins.github.api.data.GithubPullRequestCommentWithHtml
import org.jetbrains.plugins.github.pullrequest.avatars.CachingGithubAvatarIconsProvider
import org.jetbrains.plugins.github.pullrequest.data.service.GHPRReviewServiceAdapter
import org.jetbrains.plugins.github.util.GithubUIUtil
import org.jetbrains.plugins.github.util.successOnEdt
import java.awt.*
import java.awt.geom.RoundRectangle2D
import javax.swing.JComponent

class GHPRDiffEditorReviewComponentsFactoryImpl
internal constructor(private val project: Project,
                     private val reviewService: GHPRReviewServiceAdapter,
                     private val lastCommitSha: String,
                     private val filePath: String,
                     private val avatarIconsProviderFactory: CachingGithubAvatarIconsProvider.Factory,
                     private val currentUser: GHUser)
  : GHPRDiffEditorReviewComponentsFactory {

  override fun createThreadComponent(thread: GHPRReviewThreadModel): JComponent {
    val wrapper = RoundedPanel()
    val avatarIconsProvider = avatarIconsProviderFactory.create(GithubUIUtil.avatarSize, wrapper)

    val panel = JBUI.Panels.simplePanel(GHPRReviewThreadCommentsPanel(thread, avatarIconsProvider))
      .withBorder(JBUI.Borders.empty(12, 12))
      .andTransparent()

    if (reviewService.canComment()) {
      val replyField = GHPRCommentsUIUtil.createTogglableCommentField(project, avatarIconsProvider, currentUser, "Reply") { text ->
        reviewService.addComment(EmptyProgressIndicator(), text, thread.firstCommentDatabaseId).successOnEdt {
          thread.addComment(GHPRReviewCommentModel(it.nodeId, it.createdAt, it.bodyHtml, it.user.login, it.user.htmlUrl, it.user.avatarUrl))
        }
      }.apply {
        border = JBUI.Borders.emptyTop(12)
      }
      panel.addToBottom(replyField)
    }

    wrapper.setContent(panel)
    return wrapper
  }

  override fun createCommentComponent(diffLine: Int, onSuccess: (GithubPullRequestCommentWithHtml) -> Unit): JComponent {
    val wrapper = RoundedPanel()
    val avatarIconsProvider = avatarIconsProviderFactory.create(GithubUIUtil.avatarSize, wrapper)

    val commentField = GHPRCommentsUIUtil.createCommentField(project, avatarIconsProvider, currentUser, "Comment") { text ->
      reviewService.addComment(EmptyProgressIndicator(), text, lastCommitSha, filePath, diffLine).successOnEdt {
        onSuccess(it)
      }
    }.apply {
      border = JBUI.Borders.empty(12)
    }

    wrapper.setContent(commentField)
    return wrapper
  }

  private class RoundedPanel : Wrapper() {
    private var borderLineColor: Color? = null

    init {
      cursor = Cursor.getDefaultCursor()
      updateColors()
    }

    override fun updateUI() {
      super.updateUI()
      updateColors()
    }

    private fun updateColors() {
      val scheme = EditorColorsManager.getInstance().globalScheme
      background = scheme.defaultBackground
      borderLineColor = scheme.getColor(EditorColors.TEARLINE_COLOR)
    }

    override fun paintComponent(g: Graphics) {
      GraphicsUtil.setupRoundedBorderAntialiasing(g)

      val g2 = g as Graphics2D
      val rect = Rectangle(size)
      JBInsets.removeFrom(rect, insets)
      // 2.25 scale is a @#$!% so we adjust sizes manually
      val rectangle2d = RoundRectangle2D.Float(rect.x.toFloat() + 0.5f, rect.y.toFloat() + 0.5f,
                                               rect.width.toFloat() - 1f, rect.height.toFloat() - 1f,
                                               10f, 10f)
      g2.color = background
      g2.fill(rectangle2d)
      borderLineColor?.let {
        g2.color = borderLineColor
        g2.draw(rectangle2d)
      }
    }
  }
}