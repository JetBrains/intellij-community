// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.inspector.themePicker

import com.intellij.openapi.editor.ex.RangeHighlighterEx
import com.intellij.openapi.editor.impl.EditorComponentImpl
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

    val editorModel = editor.markupModel
    val documentModel = editor.filteredDocumentMarkupModel

    val processor = CommonProcessors.CollectProcessor<RangeHighlighterEx>()
    editorModel.processRangeHighlightersOverlappingWith(offset - 1, offset + 1, processor)
    documentModel.processRangeHighlightersOverlappingWith(offset - 1, offset + 1, processor)
    val highlighters = processor.results.filterIsInstance<RangeHighlighterEx>()

    val result = ArrayList<ThemeColorInfo>()
    result += highlighters.mapNotNull { it.textAttributesKey }.map { ThemeColorInfo.AttributeKeyInfo(it) }
    result += highlighters.filter { it.textAttributesKey == null }.mapNotNull { it.textAttributes }.map { ThemeColorInfo.AttributeInfo(it) }

    return result
  }
}
