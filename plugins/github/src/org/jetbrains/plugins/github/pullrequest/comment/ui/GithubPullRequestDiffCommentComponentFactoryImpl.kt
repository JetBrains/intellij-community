// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.comment.ui

import com.intellij.ide.BrowserUtil
import com.intellij.openapi.ui.VerticalFlowLayout
import com.intellij.ui.IdeBorderFactory
import com.intellij.ui.SideBorder
import com.intellij.ui.components.labels.LinkLabel
import com.intellij.util.text.DateFormatUtil
import com.intellij.util.ui.HtmlPanel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.JBValue
import com.intellij.util.ui.UIUtil
import net.miginfocom.layout.CC
import net.miginfocom.layout.LC
import net.miginfocom.swing.MigLayout
import org.jetbrains.plugins.github.api.data.GithubPullRequestCommentWithHtml
import org.jetbrains.plugins.github.pullrequest.avatars.CachingGithubAvatarIconsProvider
import java.awt.Font
import java.util.*
import javax.swing.BorderFactory
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel

class GithubPullRequestDiffCommentComponentFactoryImpl
internal constructor(private val iconProviderFactory: CachingGithubAvatarIconsProvider.Factory)
  : GithubPullRequestDiffCommentComponentFactory {

  override fun createComponent(commentsThreads: List<List<GithubPullRequestCommentWithHtml>>): JComponent {
    val threadsPanels = JPanel(VerticalFlowLayout(0, 0)).apply {
      isOpaque = false
    }

    for (thread in commentsThreads) {
      val comments = thread.sortedBy { it.createdAt }.map {
        SingleCommentModel(UserModel(it.user.login, it.user.htmlUrl!!, it.user.avatarUrl), it.createdAt, it.bodyHtml)
      }

      val commentsPanel = JPanel(VerticalFlowLayout(0, 0)).apply {
        isOpaque = false
        border = BorderFactory.createCompoundBorder(JBUI.Borders.empty(UIUtil.DEFAULT_VGAP, 0),
                                                    IdeBorderFactory.createBorder(SideBorder.TOP or SideBorder.BOTTOM))
      }
      val iconsProvider = iconProviderFactory.create(JBValue.UIInteger("key", 20), commentsPanel)
      for (comment in comments) {
        commentsPanel.add(createComponent(iconsProvider, comment))
      }
      threadsPanels.add(commentsPanel)
    }

    return threadsPanels
  }

  private fun createComponent(iconsProvider: CachingGithubAvatarIconsProvider, comment: SingleCommentModel): JComponent {
    return JPanel().apply {
      isOpaque = false
      border = JBUI.Borders.empty(UIUtil.DEFAULT_VGAP, UIUtil.DEFAULT_HGAP, UIUtil.DEFAULT_VGAP, 0)

      layout = MigLayout(LC().gridGap("0", "0")
                           .insets("0", "0", "0", "0")
                           .fill())

      val avatarIcon = iconsProvider.getIcon(comment.author.avatarUrl)
      val avatar = JLabel(avatarIcon).apply {
        border = JBUI.Borders.empty(0, UIUtil.DEFAULT_HGAP)
      }
      add(avatar, CC().spanY(2).alignY("top"))

      val username = LinkLabel.create(comment.author.username) {
        BrowserUtil.browse(comment.author.url)
      }.apply {
        border = JBUI.Borders.emptyRight(UIUtil.DEFAULT_HGAP)
      }
      add(username, CC())

      val date = JLabel(DateFormatUtil.formatPrettyDate(comment.date)).apply {
        foreground = UIUtil.getContextHelpForeground()
      }
      add(date, CC().growX().pushX().wrap())

      val textPane = object : HtmlPanel() {
        init {
          isOpaque = false
          border = JBUI.Borders.empty()
          editorKit = UIUtil.JBWordWrapHtmlEditorKit()
          update()

          putClientProperty("AuxEditorComponent", true)
        }

        override fun getBody() = comment.body

        override fun getBodyFont(): Font = UIUtil.getLabelFont()
      }

      add(textPane, CC().spanX(3).growX().minWidth("0").minHeight("0"))
    }
  }

  private data class SingleCommentModel(val author: UserModel, val date: Date, val body: String)

  private data class UserModel(val username: String, val url: String, val avatarUrl: String?)

  override fun dispose() {
  }
}