// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.view

import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.impl.EditorImpl

internal class SelectionInlayQueries(private val editor: EditorImpl) {
  fun blockInlaysAbove(bottomVisualLine: Int): List<Inlay<*>> =
    editor.inlayModel.getBlockElementsForVisualLine(bottomVisualLine - 1, false) +
      editor.inlayModel.getBlockElementsForVisualLine(bottomVisualLine, true)

  private fun isBlockInlayInSelection(inlay: Inlay<*>): Boolean = editor.caretModel.allCarets.any { caret ->
    val bounds = inlay.bounds ?: return@any false
    val startY = editor.visualPositionToXY(caret.selectionStartPosition).y
    val endY = run {
      val endPosition = caret.selectionEndPosition
      val line = if (endPosition.column == 0) endPosition.line - 1 else endPosition.line
      editor.visualLineToY(line)
    }
    val selectedRange = startY..endY

    bounds.y in selectedRange && (bounds.y + bounds.height) in selectedRange
  }

  fun isAllBlockInlaysAboveSelected(bottomVisualLine: Int): Boolean =
    blockInlaysAbove(bottomVisualLine).all { isBlockInlayInSelection(it) }
}
