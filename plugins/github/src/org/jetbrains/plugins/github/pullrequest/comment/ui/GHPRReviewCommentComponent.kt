// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.comment.ui

import com.intellij.icons.AllIcons
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.components.labels.LinkLabel
import com.intellij.ui.components.panels.Wrapper
import com.intellij.util.ui.UI
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.components.BorderLayoutPanel
import icons.GithubIcons
import net.miginfocom.layout.AC
import net.miginfocom.layout.CC
import net.miginfocom.layout.LC
import net.miginfocom.swing.MigLayout
import org.jetbrains.plugins.github.pullrequest.avatars.GHAvatarIconsProvider
import org.jetbrains.plugins.github.pullrequest.data.service.GHPRReviewServiceAdapter
import org.jetbrains.plugins.github.ui.InlineIconButton
import org.jetbrains.plugins.github.ui.util.HtmlEditorPane
import org.jetbrains.plugins.github.util.GithubUIUtil
import org.jetbrains.plugins.github.util.handleOnEdt
import org.jetbrains.plugins.github.util.successOnEdt
import java.awt.event.ActionListener
import javax.swing.JComponent
import javax.swing.JEditorPane
import javax.swing.JPanel
import javax.swing.text.BadLocationException
import javax.swing.text.Utilities

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

    val editorWrapper = Wrapper()
    val editButton = createEditButton(reviewService, comment, editorWrapper, textPane).apply {
      isVisible = comment.canBeUpdated
    }
    val deleteButton = createDeleteButton(reviewService, thread, comment).apply {
      isVisible = comment.canBeDeleted
    }

    val contentPanel = BorderLayoutPanel().andTransparent().addToCenter(textPane).addToBottom(editorWrapper)

    return JPanel(null).apply {
      isOpaque = false
      layout = MigLayout(LC().gridGap("0", "0")
                           .insets("0", "0", "0", "0")
                           .fill(),
                         AC().gap("${UI.scale(8)}"))

      add(avatarLabel, CC().pushY())
      add(titlePane, CC().minWidth("0").split(3).alignX("left"))
      add(editButton, CC().hideMode(3).gapBefore("${UI.scale(12)}"))
      add(deleteButton, CC().hideMode(3).gapBefore("${UI.scale(8)}"))
      add(contentPanel, CC().newline().skip().grow().push().minWidth("0").minHeight("0"))
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

  private fun createEditButton(reviewService: GHPRReviewServiceAdapter,
                               comment: GHPRReviewCommentModel,
                               editorWrapper: Wrapper,
                               textPane: JEditorPane): JComponent {

    val action = ActionListener {
      val linesCount = calcLines(textPane)
      val text = StringUtil.repeatSymbol('\n', linesCount - 1)

      val model = GHPRSubmittableTextField.Model { newText ->
        reviewService.updateComment(EmptyProgressIndicator(), comment.id, newText).successOnEdt {
          comment.update(it)
        }.handleOnEdt { _, _ ->
          editorWrapper.setContent(null)
          editorWrapper.revalidate()
        }
      }

      with(model.document) {
        runWriteAction {
          setText(text)
          setReadOnly(true)
        }

        reviewService.getCommentMarkdownBody(EmptyProgressIndicator(), comment.id).successOnEdt {
          runWriteAction {
            setReadOnly(false)
            setText(it)
          }
        }
      }

      val editor = GHPRSubmittableTextField.create(model, "Submit", onCancel = {
        editorWrapper.setContent(null)
        editorWrapper.revalidate()
      })
      editorWrapper.setContent(editor)
      GithubUIUtil.focusPanel(editor)
    }
    val icon = AllIcons.General.Inline_edit
    val hoverIcon = AllIcons.General.Inline_edit_hovered
    return InlineIconButton(icon, hoverIcon, tooltip = "Edit").apply {
      actionListener = action
    }
  }


  private fun calcLines(textPane: JEditorPane): Int {
    var lineCount = 0
    var offset = 0
    while (true) {
      try {
        offset = Utilities.getRowEnd(textPane, offset) + 1
        lineCount++
      }
      catch (e: BadLocationException) {
        break
      }
    }
    return lineCount
  }

  fun factory(thread: GHPRReviewThreadModel, reviewService: GHPRReviewServiceAdapter, avatarIconsProvider: GHAvatarIconsProvider)
    : (GHPRReviewCommentModel) -> JComponent {
    return { comment ->
      create(reviewService, thread, comment, avatarIconsProvider)
    }
  }
}