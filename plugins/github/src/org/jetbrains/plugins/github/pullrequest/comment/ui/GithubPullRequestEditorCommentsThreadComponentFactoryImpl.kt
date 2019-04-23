// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.comment.ui

import com.intellij.ide.BrowserUtil
import com.intellij.openapi.ui.VerticalFlowLayout
import com.intellij.ui.components.labels.LinkLabel
import com.intellij.util.text.DateFormatUtil
import com.intellij.util.ui.HtmlPanel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.JBValue
import com.intellij.util.ui.UIUtil
import net.miginfocom.layout.CC
import net.miginfocom.layout.LC
import net.miginfocom.swing.MigLayout
import org.jetbrains.plugins.github.pullrequest.avatars.CachingGithubAvatarIconsProvider
import org.jetbrains.plugins.github.pullrequest.comment.ui.model.GithubPullRequestFileComment
import org.jetbrains.plugins.github.pullrequest.comment.ui.model.GithubPullRequestFileCommentsThread
import java.awt.Font
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.event.ListDataEvent
import javax.swing.event.ListDataListener

class GithubPullRequestEditorCommentsThreadComponentFactoryImpl
internal constructor(private val avatarIconsProviderFactory: CachingGithubAvatarIconsProvider.Factory)
  : GithubPullRequestEditorCommentsThreadComponentFactory {

  override fun createComponent(thread: GithubPullRequestFileCommentsThread): JComponent {
    val threadPanel = JPanel(VerticalFlowLayout(JBUI.scale(UIUtil.DEFAULT_HGAP), JBUI.scale(UIUtil.DEFAULT_VGAP))).apply {
      isOpaque = false
      border = JBUI.Borders.empty()
    }
    val avatarsProvider = avatarIconsProviderFactory.create(JBValue.UIInteger("GitHub.Avatar.Size", 20), threadPanel)
    for (comment in thread.items) {
      threadPanel.add(createComponent(avatarsProvider, comment))
    }
    thread.addListDataListener(object : ListDataListener {
      override fun intervalRemoved(e: ListDataEvent) {
        for (i in e.index0..e.index1)
          threadPanel.remove(i)
      }

      override fun intervalAdded(e: ListDataEvent) {
        for (i in e.index0..e.index1)
          threadPanel.add(createComponent(avatarsProvider, thread.getElementAt(i)), i)
      }

      override fun contentsChanged(e: ListDataEvent) {
        for (i in e.index0..e.index1) {
          threadPanel.remove(i)
          threadPanel.add(createComponent(avatarsProvider, thread.getElementAt(i)), i)
        }
      }
    })
    return threadPanel
  }

  private fun createComponent(avatarsProvider: CachingGithubAvatarIconsProvider, comment: GithubPullRequestFileComment): JComponent {
    return JPanel().apply {
      isOpaque = false
      border = JBUI.Borders.empty(UIUtil.DEFAULT_VGAP, UIUtil.DEFAULT_HGAP, UIUtil.DEFAULT_VGAP, 0)

      layout = MigLayout(LC().gridGap("0", "0")
                           .insets("0", "0", "0", "0")
                           .fill())

      val avatarIcon = avatarsProvider.getIcon(comment.authorAvatarUrl)
      val avatar = JLabel(avatarIcon).apply {
        border = JBUI.Borders.emptyRight(UIUtil.DEFAULT_HGAP)
      }
      add(avatar, CC().spanY(2).alignY("top"))

      val username = LinkLabel.create(comment.authorUsername) {
        BrowserUtil.browse(comment.authorLinkUrl)
      }.apply {
        border = JBUI.Borders.emptyRight(UIUtil.DEFAULT_HGAP)
      }
      add(username, CC())

      val date = JLabel(DateFormatUtil.formatPrettyDate(comment.dateCreated)).apply {
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
}