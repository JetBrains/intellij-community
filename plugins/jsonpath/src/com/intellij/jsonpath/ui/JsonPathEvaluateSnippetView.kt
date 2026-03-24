// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.jsonpath.ui

import com.intellij.json.psi.JsonFile
import com.intellij.jsonpath.JsonPathBundle
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.EditorKind
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.ex.ToolWindowManagerListener
import com.intellij.psi.PsiDocumentManager
import com.intellij.ui.JBColor
import com.intellij.ui.OnePixelSplitter
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.components.BorderLayoutPanel
import com.intellij.util.ui.update.DebouncedUpdates
import com.intellij.util.ui.update.UpdateQueue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import java.awt.event.KeyEvent
import javax.swing.FocusManager
import javax.swing.KeyStroke
import javax.swing.SwingUtilities
import kotlin.time.Duration.Companion.milliseconds

internal class JsonPathEvaluateSnippetView(project: Project, scope: CoroutineScope) : JsonPathEvaluateView(project) {
  private val expressionHighlightingQueue: UpdateQueue<Unit> = DebouncedUpdates.forScope<Unit>(scope, "JSONPATH_EVALUATE", 1000.milliseconds)
    .withContext(Dispatchers.EDT)
    .runLatest { resetExpressionHighlighting() }
    .cancelOnDispose(this)
  private val sourceEditor: Editor = initJsonEditor("source.json", false, EditorKind.UNTYPED)

  init {
    val sourcePanel = BorderLayoutPanel()
    sourcePanel.addToTop(searchWrapper)

    val sourceWrapper = BorderLayoutPanel()
    val sourceLabel = JBLabel(JsonPathBundle.message("jsonpath.evaluate.input"))
    sourceLabel.border = JBUI.Borders.empty(3, 6)
    sourceWrapper.addToTop(sourceLabel)
    sourceWrapper.addToCenter(sourceEditor.component)

    sourcePanel.addToCenter(sourceWrapper)

    val splitter = OnePixelSplitter(0.5f)
    splitter.firstComponent = sourcePanel
    splitter.secondComponent = resultWrapper

    setContent(splitter)
    setSource("{\n\n}")

    sourceEditor.component.border = JBUI.Borders.customLine(JBColor.border(), 1, 0, 0, 0)
    sourceEditor.document.addDocumentListener(object : DocumentListener {
      override fun documentChanged(event: DocumentEvent) {
        expressionHighlightingQueue.queue(Unit)
      }
    })

    val messageBusConnection = this.project.messageBus.connect(this)
    messageBusConnection.subscribe(ToolWindowManagerListener.TOPIC, object : ToolWindowManagerListener {
      override fun stateChanged(toolWindowManager: ToolWindowManager) {
        val toolWindow = toolWindowManager.getToolWindow(JsonPathEvaluateManager.EVALUATE_TOOLWINDOW_ID)
        if (toolWindow != null) {
          splitter.orientation = !toolWindow.anchor.isHorizontal
        }
      }
    })

    initToolbar()
  }

  private fun setSource(json: String) {
    WriteAction.run<Throwable> {
      sourceEditor.document.setText(json)
    }
  }

  override fun getJsonFile(): JsonFile? {
    return PsiDocumentManager.getInstance(project).getPsiFile(sourceEditor.document) as? JsonFile
  }

  override fun processKeyBinding(ks: KeyStroke?, e: KeyEvent?, condition: Int, pressed: Boolean): Boolean {
    if (pressed && e?.keyCode == KeyEvent.VK_ESCAPE) {
      val focusOwner = FocusManager.getCurrentManager().focusOwner

      if (SwingUtilities.isDescendingFrom(focusOwner, sourceEditor.component)) {
        searchTextField.requestFocus()
        return true
      }
    }
    return super.processKeyBinding(ks, e, condition, pressed)
  }

  override fun dispose() {
    super.dispose()

    EditorFactory.getInstance().releaseEditor(sourceEditor)
  }
}