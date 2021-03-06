// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.ui.codereview.timeline

import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.EditorKind
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.project.Project
import com.intellij.util.ui.JBUI
import javax.swing.JComponent

class TimelineDiffComponentFactory(private val project: Project, private val editorFactory: EditorFactory) {
  fun createDiffComponent(text: CharSequence, modifyEditor: (EditorEx) -> Unit): JComponent =
    EditorHandlerPanel.create(editorFactory) { factory ->
      val editor = createSimpleDiffEditor(project, factory, text)
      modifyEditor(editor)
      editor
    }

  private fun createSimpleDiffEditor(project: Project, editorFactory: EditorFactory, text: CharSequence): EditorEx {
    return (editorFactory.createViewer(editorFactory.createDocument(text), project, EditorKind.DIFF) as EditorEx).apply {
      gutterComponentEx.setPaintBackground(false)

      setHorizontalScrollbarVisible(true)
      setVerticalScrollbarVisible(false)
      setCaretEnabled(false)

      setBorder(JBUI.Borders.empty())

      settings.apply {
        isCaretRowShown = false
        additionalLinesCount = 0
        additionalColumnsCount = 0
        isRightMarginShown = false
        setRightMargin(-1)
        isFoldingOutlineShown = false
        isIndentGuidesShown = false
        isVirtualSpace = false
        isWheelFontChangeEnabled = false
        isAdditionalPageAtBottom = false
        lineCursorWidth = 1
      }
    }
  }
}