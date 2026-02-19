// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.inspector.themePicker

import com.intellij.openapi.editor.ex.RangeHighlighterEx
import com.intellij.openapi.editor.impl.EditorComponentImpl
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.ui.awt.RelativePoint
import com.intellij.util.CommonProcessors
import com.intellij.util.ui.UIUtil

internal object EditorColorPicker {
  fun getEditorColorsAt(point: RelativePoint): List<ThemeColorInfo>? {
    val component = UIUtil.getDeepestComponentAt(point.component, point.point.x, point.point.y) ?: return null
    val editorComponent = component as? EditorComponentImpl ?: return null
    val editor = editorComponent.editor

    val position = editor.xyToLogicalPosition(point.getPoint(editorComponent))
    val offset = editor.logicalPositionToOffset(position)

    val result = mutableListOf<ThemeColorInfo>()

    processMarkupModel(result, editor, offset)
    processSyntaxModel(result, editor, offset)

    return result
  }

  private fun processMarkupModel(result: MutableList<ThemeColorInfo>, editor: EditorImpl, offset: Int) {
    val editorModel = editor.markupModel
    val documentModel = editor.filteredDocumentMarkupModel

    val processor = CommonProcessors.CollectProcessor<RangeHighlighterEx>()
    editorModel.processRangeHighlightersOverlappingWith(offset - 1, offset + 1, processor)
    documentModel.processRangeHighlightersOverlappingWith(offset - 1, offset + 1, processor)
    val highlighters = processor.results.filterIsInstance<RangeHighlighterEx>().sortedByDescending { it.layer }

    for (highlighterEx in highlighters) {
      val text = editor.document.immutableCharSequence.substring(highlighterEx.startOffset, highlighterEx.endOffset)
      val highlighterInfo = ThemeColorInfo.HighlighterInfo(text.toString(),
                                                           highlighterEx.forcedTextAttributes,
                                                           highlighterEx.textAttributesKey,
                                                           highlighterEx.textAttributes)
      if (!highlighterInfo.isEmpty) {
        result += highlighterInfo
      }
    }
  }

  private fun processSyntaxModel(result: MutableList<ThemeColorInfo>, editor: EditorImpl, offset: Int) {
    val iterator = editor.highlighter.createIterator(offset - 1)

    while (!iterator.atEnd() && iterator.start <= offset + 1) {
      val text = editor.document.immutableCharSequence.substring(iterator.start, iterator.end)
      val syntaxInfo = ThemeColorInfo.SyntaxInfo(text, iterator.textAttributesKeys, iterator.textAttributes)
      if (!syntaxInfo.isEmpty) {
        result += syntaxInfo
      }
      iterator.advance()
    }
  }
}
