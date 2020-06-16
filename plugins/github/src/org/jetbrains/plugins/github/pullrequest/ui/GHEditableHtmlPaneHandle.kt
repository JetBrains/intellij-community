// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.ui

import com.intellij.CommonBundle
import com.intellij.ide.plugins.newui.VerticalLayout
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.components.panels.NonOpaquePanel
import com.intellij.util.ui.UI
import org.jetbrains.plugins.github.pullrequest.comment.ui.GHPreLoadingSubmittableTextFieldModel
import org.jetbrains.plugins.github.pullrequest.comment.ui.GHSubmittableTextFieldFactory
import org.jetbrains.plugins.github.ui.util.HtmlEditorPane
import org.jetbrains.plugins.github.util.GithubUIUtil
import org.jetbrains.plugins.github.util.successOnEdt
import java.util.concurrent.CompletableFuture
import javax.swing.JComponent
import javax.swing.text.BadLocationException
import javax.swing.text.Utilities

internal class GHEditableHtmlPaneHandle(private val editorPane: HtmlEditorPane,
                                        private val loadSource: () -> CompletableFuture<String>,
                                        private val updateText: (String) -> CompletableFuture<out Any?>) {

  val panel = NonOpaquePanel(VerticalLayout(UI.scale(8))).apply {
    add(editorPane)
  }

  private var editor: JComponent? = null

  fun showAndFocusEditor() {
    if (editor == null) {
      val placeHolderText = StringUtil.repeatSymbol('\n', Integer.max(0, getLineCount() - 1))

      val model = GHPreLoadingSubmittableTextFieldModel(placeHolderText, loadSource()) { newText ->
        updateText(newText).successOnEdt {
          hideEditor()
        }
      }

      editor = GHSubmittableTextFieldFactory(model).create(CommonBundle.message("button.submit"), onCancel = {
        hideEditor()
      })
      panel.add(editor!!, VerticalLayout.FILL_HORIZONTAL)
      panel.validate()
      panel.repaint()
    }

    editor?.let { GithubUIUtil.focusPanel(it) }
  }

  private fun hideEditor() {
    editor?.let {
      panel.remove(it)
      panel.revalidate()
      panel.repaint()
    }
    editor = null
  }

  private fun getLineCount(): Int {
    if (editorPane.document.length == 0) return 0
    var lineCount = 0
    var offset = 0
    while (true) {
      try {
        offset = Utilities.getRowEnd(editorPane, offset) + 1
        lineCount++
      }
      catch (e: BadLocationException) {
        break
      }
    }
    return lineCount
  }
}
