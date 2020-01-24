// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.comment.ui

import com.intellij.ide.BrowserUtil
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.ui.Messages
import com.intellij.ui.components.labels.LinkLabel
import com.intellij.util.ui.UI
import com.intellij.util.ui.UIUtil
import icons.GithubIcons
import net.miginfocom.layout.AC
import net.miginfocom.layout.CC
import net.miginfocom.layout.LC
import net.miginfocom.swing.MigLayout
import org.jetbrains.plugins.github.pullrequest.avatars.GHAvatarIconsProvider
import org.jetbrains.plugins.github.pullrequest.data.service.GHPRReviewServiceAdapter
import org.jetbrains.plugins.github.ui.util.HtmlEditorPane
import org.jetbrains.plugins.github.util.GithubUIUtil
import java.awt.event.ActionListener
import javax.swing.JComponent
import javax.swing.JPanel

object GHPRReviewCommentComponent {

  fun create(reviewService: GHPRReviewServiceAdapter,
             thread: GHPRReviewThreadModel, comment: GHPRReviewCommentModel,
             avatarIconsProvider: GHAvatarIconsProvider): JComponent {

    val avatarLabel: LinkLabel<*> = LinkLabel.create("") {
      comment.authorLinkUrl?.let { BrowserUtil.browse(it) }
    }.apply {
      icon = avatarIconsProvider.getIcon(comment.authorAvatarUrl)
      isFocusable = true
      putClientProperty(UIUtil.HIDE_EDITOR_FROM_DATA_CONTEXT_PROPERTY, true)
    }

    val href = comment.authorLinkUrl?.let { """href='${it}'""" }.orEmpty()
    //language=HTML
    val title = """<a $href>${comment.authorUsername ?: "unknown"}</a> commented ${GithubUIUtil.formatActionDate(comment.dateCreated)}"""

    val titlePane: HtmlEditorPane = HtmlEditorPane(title).apply {
      foreground = UIUtil.getContextHelpForeground()
      putClientProperty(UIUtil.HIDE_EDITOR_FROM_DATA_CONTEXT_PROPERTY, true)
    }

    val textPane: HtmlEditorPane = HtmlEditorPane(comment.body).apply {
      putClientProperty(UIUtil.HIDE_EDITOR_FROM_DATA_CONTEXT_PROPERTY, true)
    }

    comment.addChangesListener {
      textPane.setBody(comment.body)
    }

    val deleteButton = createDeleteButton(reviewService, thread, comment).apply {
      isVisible = comment.canBeDeleted
    }

    return JPanel(null).apply {
      isOpaque = false
      layout = MigLayout(LC().gridGap("0", "0")
                           .insets("0", "0", "0", "0")
                           .fill(),
                         AC().gap("${UI.scale(8)}").gap("${UI.scale(12)}").gap("push"))

      add(avatarLabel, CC().pushY())
      add(titlePane, CC().growX().pushX().minWidth("0"))
      add(deleteButton, CC().hideMode(3))
      add(textPane, CC().newline().skip().spanX(2).grow().push().minWidth("0").minHeight("0"))
    }
  }

  private fun createDeleteButton(reviewService: GHPRReviewServiceAdapter,
                                 thread: GHPRReviewThreadModel,
                                 comment: GHPRReviewCommentModel): JComponent {
    val icon = GithubIcons.Delete
    val hoverIcon = GithubIcons.DeleteHovered
    return InlineIconButton(icon, hoverIcon, tooltip = "Delete").apply {
      actionListener = ActionListener {
        if (Messages.showConfirmationDialog(this, "Are you sure you want to delete this comment?", "Delete Comment",
                                            Messages.getYesButton(), Messages.getNoButton()) == Messages.YES) {
          reviewService.deleteComment(EmptyProgressIndicator(), comment.id)
          thread.removeComment(comment)
        }
      }
    }
  }

  fun factory(thread: GHPRReviewThreadModel, reviewService: GHPRReviewServiceAdapter, avatarIconsProvider: GHAvatarIconsProvider)
    : (GHPRReviewCommentModel) -> JComponent {
    return { comment ->
      create(reviewService, thread, comment, avatarIconsProvider)
    }
  }
}