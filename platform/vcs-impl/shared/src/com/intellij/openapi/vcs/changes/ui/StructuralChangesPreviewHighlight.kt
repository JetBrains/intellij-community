// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes.ui

import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.markup.CustomHighlighterOrder
import com.intellij.openapi.editor.markup.CustomHighlighterRenderer
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.util.TextRange
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.ui.GraphicsUtil
import org.jetbrains.annotations.ApiStatus
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Rectangle
import java.awt.geom.Area
import java.text.Bidi

@ApiStatus.Internal
@RequiresEdt
fun highlightStructuralPreview(editor: Editor, range: TextRange): Disposable? {
  if (editor.isDisposed || range.isEmpty || range.startOffset < 0 || range.endOffset > editor.document.textLength) return null
  if (hasBidirectionalBoundary(editor, range)) return null
  val highlighter = editor.markupModel
    .addRangeHighlighter(0, editor.document.textLength, HighlighterLayer.LAST, null, HighlighterTargetArea.EXACT_RANGE)
    .apply { customRenderer = StructuralPreviewDimRenderer(range) }
  return Disposable { if (highlighter.isValid) highlighter.dispose() }
}

private fun hasBidirectionalBoundary(editor: Editor, range: TextRange): Boolean {
  val document = editor.document
  val folding = editor.foldingModel
  val startOffset = folding.getCollapsedRegionAtOffset(range.startOffset)?.startOffset ?: range.startOffset
  val endOffset = folding.getCollapsedRegionAtOffset(range.endOffset - 1)?.endOffset ?: range.endOffset
  val startLine = document.getLineNumber(startOffset)
  val endLine = document.getLineNumber(endOffset)
  return listOf(startLine, endLine).distinct().any { line ->
    val lineStart = document.getLineStartOffset(line)
    val content = document.charsSequence
    val text = CharArray(document.getLineEndOffset(line) - lineStart) { content[lineStart + it] }
    Bidi.requiresBidi(text, 0, text.size)
  }
}

private class StructuralPreviewDimRenderer(private val selectedRange: TextRange) : CustomHighlighterRenderer {
  override fun getOrder(): CustomHighlighterOrder = CustomHighlighterOrder.AFTER_TEXT

  override fun paint(editor: Editor, highlighter: RangeHighlighter, g: Graphics) {
    if (editor.isDisposed || selectedRange.endOffset > editor.document.textLength) return
    val graphics = g.create() as Graphics2D
    try {
      val clip = graphics.clipBounds ?: editor.scrollingModel.visibleArea
      val dimmedArea = Area(clip)
      val folding = editor.foldingModel
      val startOffset = folding.getCollapsedRegionAtOffset(selectedRange.startOffset)?.startOffset ?: selectedRange.startOffset
      val endOffset = folding.getCollapsedRegionAtOffset(selectedRange.endOffset - 1)?.endOffset ?: selectedRange.endOffset
      val start = editor.offsetToXY(startOffset, true, false)
      val end = editor.offsetToXY(endOffset, false, true)
      val lineHeight = editor.lineHeight

      if (start.y == end.y) {
        dimmedArea.subtract(Area(Rectangle(minOf(start.x, end.x), start.y, kotlin.math.abs(end.x - start.x), lineHeight)))
      }
      else {
        dimmedArea.subtract(Area(Rectangle(start.x, start.y, (clip.x + clip.width - start.x).coerceAtLeast(0), lineHeight)))
        dimmedArea.subtract(Area(Rectangle(clip.x, start.y + lineHeight, clip.width, (end.y - start.y - lineHeight).coerceAtLeast(0))))
        dimmedArea.subtract(Area(Rectangle(clip.x, end.y, (end.x - clip.x).coerceAtLeast(0), lineHeight)))
      }

      graphics.color = editor.colorsScheme.defaultBackground
      GraphicsUtil.paintWithAlpha(graphics, 0.65f) { graphics.fill(dimmedArea) }
    }
    finally {
      graphics.dispose()
    }
  }
}
