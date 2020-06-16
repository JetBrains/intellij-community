// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.ui

import com.intellij.CommonBundle
import com.intellij.icons.AllIcons
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.components.panels.Wrapper
import icons.GithubIcons
import org.jetbrains.plugins.github.i18n.GithubBundle
import org.jetbrains.plugins.github.pullrequest.comment.ui.GHPreLoadingSubmittableTextFieldModel
import org.jetbrains.plugins.github.pullrequest.comment.ui.GHSubmittableTextFieldFactory
import org.jetbrains.plugins.github.ui.InlineIconButton
import org.jetbrains.plugins.github.util.GithubUIUtil
import org.jetbrains.plugins.github.util.successOnEdt
import java.awt.event.ActionListener
import java.util.concurrent.CompletableFuture
import javax.swing.JComponent
import javax.swing.JEditorPane
import javax.swing.text.BadLocationException
import javax.swing.text.Utilities

object GHTextActions {

  fun createDeleteButton(delete: () -> CompletableFuture<out Any?>): JComponent {
    val icon = GithubIcons.Delete
    val hoverIcon = GithubIcons.DeleteHovered
    return InlineIconButton(icon, hoverIcon, tooltip = CommonBundle.message("button.delete")).apply {
      actionListener = ActionListener {
        if (Messages.showConfirmationDialog(this, GithubBundle.message("pull.request.review.comment.delete.dialog.msg"),
                                            GithubBundle.message("pull.request.review.comment.delete.dialog.title"),
                                            Messages.getYesButton(), Messages.getNoButton()) == Messages.YES) {
          delete()
        }
      }
    }
  }

  fun createEditButton(textPane: JEditorPane, editorWrapper: Wrapper,
                       loadSource: () -> CompletableFuture<String>,
                       updateText: (String) -> CompletableFuture<out Any?>): JComponent {

    val action = ActionListener {
      val linesCount = calcLines(textPane)
      val placeHolderText = StringUtil.repeatSymbol('\n', Integer.max(0, linesCount - 1))
      val textFuture = loadSource()

      val model = GHPreLoadingSubmittableTextFieldModel(placeHolderText, textFuture) { newText ->
        updateText(newText).successOnEdt {
          editorWrapper.setContent(null)
          editorWrapper.revalidate()
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
    if (textPane.document.length == 0) return 0
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
}