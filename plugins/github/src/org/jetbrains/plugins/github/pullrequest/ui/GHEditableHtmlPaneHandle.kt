// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.ui

import com.intellij.CommonBundle
import com.intellij.collaboration.async.CompletableFutureUtil.successOnEdt
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.components.panels.NonOpaquePanel
import com.intellij.ui.components.panels.VerticalLayout
import org.jetbrains.plugins.github.pullrequest.comment.ui.GHPreLoadingCommentTextFieldModel
import org.jetbrains.plugins.github.pullrequest.comment.ui.GHCommentTextFieldFactory
import org.jetbrains.plugins.github.ui.util.GHUIUtil
import org.jetbrains.plugins.github.ui.util.HtmlEditorPane
import java.util.concurrent.CompletableFuture
import javax.swing.JComponent
import javax.swing.text.BadLocationException
import javax.swing.text.Utilities

internal open class GHEditableHtmlPaneHandle(private val project: Project,
                                             private val editorPane: HtmlEditorPane,
                                             private val loadSource: () -> CompletableFuture<String>,
                                             private val updateText: (String) -> CompletableFuture<out Any?>) {

  val panel = NonOpaquePanel(VerticalLayout(8, VerticalLayout.FILL)).apply {
    add(wrapEditorPane(editorPane))
  }

  protected open fun wrapEditorPane(editorPane: HtmlEditorPane): JComponent = editorPane

  private var editor: JComponent? = null

  fun showAndFocusEditor() {
    if (editor == null) {
      val placeHolderText = StringUtil.repeatSymbol('\n', Integer.max(0, getLineCount() - 1))

      val model = GHPreLoadingCommentTextFieldModel(project, placeHolderText, loadSource()) { newText ->
        updateText(newText).successOnEdt {
          hideEditor()
        }
      }

      editor = GHCommentTextFieldFactory(model).create(CommonBundle.message("button.submit"), onCancel = {
        hideEditor()
      })
      panel.add(editor!!)
      panel.validate()
      panel.repaint()
    }

    editor?.let { GHUIUtil.focusPanel(it) }
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