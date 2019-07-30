// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.comment.ui

import com.intellij.ide.BrowserUtil
import com.intellij.ui.components.labels.LinkLabel
import com.intellij.util.ui.UI
import com.intellij.util.ui.UIUtil
import net.miginfocom.layout.CC
import net.miginfocom.layout.LC
import net.miginfocom.swing.MigLayout
import org.jetbrains.plugins.github.pullrequest.avatars.GHAvatarIconsProvider
import org.jetbrains.plugins.github.ui.util.HtmlEditorPane
import org.jetbrains.plugins.github.util.GithubUIUtil
import javax.swing.JPanel

class GHPRReviewCommentComponent(avatarIconsProvider: GHAvatarIconsProvider, comment: GHPRReviewCommentModel) : JPanel() {

  private val avatarLabel: LinkLabel<*>
  private val titlePane: HtmlEditorPane
  private val textPane: HtmlEditorPane

  init {
    isOpaque = false
    layout = MigLayout(LC().gridGap("0", "0")
                         .insets("0", "0", "0", "0")
                         .fill()).apply {
      columnConstraints = "[]${UI.scale(8)}[]"
    }

    avatarLabel = LinkLabel.create("") {
      comment.authorLinkUrl?.let { BrowserUtil.browse(it) }
    }.apply {
      icon = avatarIconsProvider.getIcon(comment.authorAvatarUrl)
      isFocusable = true
      putClientProperty(UIUtil.HIDE_EDITOR_FROM_DATA_CONTEXT_PROPERTY, true)
    }

    val href = comment.authorLinkUrl?.let { """href='${it}'""" }.orEmpty()
    //language=HTML
    val title = """<a $href>${comment.authorUsername ?: "unknown"}</a> 
      commented ${GithubUIUtil.formatActionDate(comment.dateCreated)}"""

    titlePane = HtmlEditorPane(title).apply {
      foreground = UIUtil.getContextHelpForeground()
      putClientProperty(UIUtil.HIDE_EDITOR_FROM_DATA_CONTEXT_PROPERTY, true)
    }

    textPane = HtmlEditorPane(comment.body).apply {
      putClientProperty(UIUtil.HIDE_EDITOR_FROM_DATA_CONTEXT_PROPERTY, true)
    }

    add(avatarLabel, CC().pushY())
    add(titlePane, CC().growX().pushX().minWidth("0"))
    add(textPane, CC().newline().skip().grow().push().minWidth("0").minHeight("0"))

    comment.addChangesListener {
      textPane.setBody(comment.body)
    }
  }
}