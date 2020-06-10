// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.comment.ui

import com.intellij.CommonBundle
import com.intellij.icons.AllIcons
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.labels.LinkLabel
import com.intellij.ui.components.panels.Wrapper
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UI
import com.intellij.util.ui.UIUtil
import icons.GithubIcons
import net.miginfocom.layout.AC
import net.miginfocom.layout.CC
import net.miginfocom.layout.LC
import net.miginfocom.swing.MigLayout
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestReviewCommentState
import org.jetbrains.plugins.github.i18n.GithubBundle
import org.jetbrains.plugins.github.pullrequest.avatars.GHAvatarIconsProvider
import org.jetbrains.plugins.github.pullrequest.data.provider.GHPRReviewDataProvider
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

  fun create(reviewDataProvider: GHPRReviewDataProvider,
             comment: GHPRReviewCommentModel,
             avatarIconsProvider: GHAvatarIconsProvider): JComponent {

    val avatarLabel: LinkLabel<*> = LinkLabel.create("") {
      comment.authorLinkUrl?.let { BrowserUtil.browse(it) }
    }.apply {
      icon = avatarIconsProvider.getIcon(comment.authorAvatarUrl)
      isFocusable = true
      putClientProperty(UIUtil.HIDE_EDITOR_FROM_DATA_CONTEXT_PROPERTY, true)
    }

    val titlePane = HtmlEditorPane().apply {
      foreground = UIUtil.getContextHelpForeground()
      putClientProperty(UIUtil.HIDE_EDITOR_FROM_DATA_CONTEXT_PROPERTY, true)
    }
    val pendingLabel = JBLabel(" ${GithubBundle.message("pull.request.review.comment.pending")} ", UIUtil.ComponentStyle.SMALL).apply {
      foreground = UIUtil.getContextHelpForeground()
      background = JBUI.CurrentTheme.Validator.warningBackgroundColor()
    }.andOpaque()
    val resolvedLabel = JBLabel(" ${GithubBundle.message("pull.request.review.comment.resolved")} ", UIUtil.ComponentStyle.SMALL).apply {
      foreground = UIUtil.getContextHelpForeground()
      background = UIUtil.getPanelBackground()
    }.andOpaque()

    val textPane = HtmlEditorPane().apply {
      putClientProperty(UIUtil.HIDE_EDITOR_FROM_DATA_CONTEXT_PROPERTY, true)
    }


    Controller(comment, titlePane, pendingLabel, resolvedLabel, textPane)

    val editorWrapper = Wrapper()
    val editButton = createEditButton(reviewDataProvider, comment, editorWrapper, textPane).apply {
      isVisible = comment.canBeUpdated
    }
    val deleteButton = createDeleteButton(reviewDataProvider, comment).apply {
      isVisible = comment.canBeDeleted
    }

    return JPanel(null).apply {
      isOpaque = false
      layout = MigLayout(LC().gridGap("0", "0")
                           .insets("0", "0", "0", "0")
                           .fill(),
                         AC().gap("${UI.scale(8)}"))

      add(avatarLabel, CC().pushY())
      add(titlePane, CC().minWidth("0").split(5).alignX("left").pushX())
      add(pendingLabel, CC().hideMode(3).alignX("left"))
      add(resolvedLabel, CC().hideMode(3).alignX("left"))
      add(editButton, CC().hideMode(3).gapBefore("${UI.scale(12)}"))
      add(deleteButton, CC().hideMode(3).gapBefore("${UI.scale(8)}"))
      add(textPane, CC().newline().skip().push().minWidth("0").minHeight("0"))
      add(editorWrapper, CC().newline().skip().push().minWidth("0").minHeight("0").growX())
    }
  }

  private fun createDeleteButton(reviewDataProvider: GHPRReviewDataProvider,
                                 comment: GHPRReviewCommentModel): JComponent {
    val icon = GithubIcons.Delete
    val hoverIcon = GithubIcons.DeleteHovered
    return InlineIconButton(icon, hoverIcon, tooltip = CommonBundle.message("button.delete")).apply {
      actionListener = ActionListener {
        if (Messages.showConfirmationDialog(this, GithubBundle.message("pull.request.review.comment.delete.dialog.msg"),
                                            GithubBundle.message("pull.request.review.comment.delete.dialog.title"),
                                            Messages.getYesButton(), Messages.getNoButton()) == Messages.YES) {
          reviewDataProvider.deleteComment(EmptyProgressIndicator(), comment.id)
        }
      }
    }
  }

  private fun createEditButton(reviewDataProvider: GHPRReviewDataProvider,
                               comment: GHPRReviewCommentModel,
                               editorWrapper: Wrapper,
                               textPane: JEditorPane): JComponent {

    val action = ActionListener {
      val linesCount = calcLines(textPane)
      val text = StringUtil.repeatSymbol('\n', linesCount - 1)

      val model = GHSubmittableTextFieldModel { newText ->
        reviewDataProvider.updateComment(EmptyProgressIndicator(), comment.id, newText).handleOnEdt { _, _ ->
          editorWrapper.setContent(null)
          editorWrapper.revalidate()
        }
      }

      with(model.document) {
        runWriteAction {
          setText(text)
          setReadOnly(true)
        }
        model.isLoading = true
        reviewDataProvider.getCommentMarkdownBody(EmptyProgressIndicator(), comment.id).successOnEdt {
          runWriteAction {
            setReadOnly(false)
            setText(it)
          }
          model.isLoading = false
        }
      }

      val editor = GHSubmittableTextFieldFactory(model).create(CommonBundle.message("button.submit"), onCancel = {
        editorWrapper.setContent(null)
        editorWrapper.revalidate()
      })
      editorWrapper.setContent(editor)
      GithubUIUtil.focusPanel(editor)
    }
    val icon = AllIcons.General.Inline_edit
    val hoverIcon = AllIcons.General.Inline_edit_hovered
    return InlineIconButton(icon, hoverIcon, tooltip = CommonBundle.message("button.edit")).apply {
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

  private class Controller(private val model: GHPRReviewCommentModel,
                           private val titlePane: HtmlEditorPane,
                           private val pendingLabel: JComponent,
                           private val resolvedLabel: JComponent,
                           private val bodyPane: HtmlEditorPane) {
    init {
      model.addChangesListener {
        update()
      }
      update()
    }

    private fun update() {
      bodyPane.setBody(model.body)

      val href = model.authorLinkUrl?.let { """href='${it}'""" }.orEmpty()
      //language=HTML
      val authorName = """<a $href>${model.authorUsername ?: "unknown"}</a>"""

      when (model.state) {
        GHPullRequestReviewCommentState.PENDING -> {
          pendingLabel.isVisible = true
          titlePane.text = authorName
        }
        GHPullRequestReviewCommentState.SUBMITTED -> {
          pendingLabel.isVisible = false
          titlePane.text = GithubBundle.message("pull.request.review.commented", authorName,
                                                GithubUIUtil.formatActionDate(model.dateCreated))
        }
      }

      resolvedLabel.isVisible = model.isFirstInResolvedThread
    }
  }

  fun factory(reviewDataProvider: GHPRReviewDataProvider, avatarIconsProvider: GHAvatarIconsProvider)
    : (GHPRReviewCommentModel) -> JComponent {
    return { comment ->
      create(reviewDataProvider, comment, avatarIconsProvider)
    }
  }
}