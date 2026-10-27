// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes.ui

import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.markup.CustomHighlighterOrder
import com.intellij.openapi.editor.markup.CustomHighlighterRenderer
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vcs.changes.StructuralChangesViewSettings
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.ui.GraphicsUtil
import org.jetbrains.annotations.ApiStatus
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Rectangle
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.geom.Area
import java.text.Bidi
import javax.swing.JComponent

/**
 * Dims the editor outside [range]. With [wholeLines], the range keeps the full width of its lines, as a diff hunk does, and an empty
 * range keeps one blank line. A null range dims the whole editor: a side of a diff hunk can have no lines. With [gutter], the gutter gets
 * the same dim, and the dim also covers the line numbers. The strength comes from [StructuralChangesViewSettings.previewDimAlpha].
 */
@ApiStatus.Internal
@RequiresEdt
fun highlightStructuralPreview(editor: Editor, range: TextRange?, wholeLines: Boolean = false, gutter: Boolean = false): Disposable? {
  if (editor.isDisposed) return null
  if (range != null) {
    if (range.startOffset < 0 || range.endOffset > editor.document.textLength) return null
    if (!wholeLines && (range.isEmpty || hasBidirectionalBoundary(editor, range))) return null
  }
  val highlighter = editor.markupModel
    .addRangeHighlighter(0, editor.document.textLength, HighlighterLayer.LAST, null, HighlighterTargetArea.EXACT_RANGE)
    .apply { customRenderer = StructuralPreviewDimRenderer(range, wholeLines) }
  val gutterDim = if (gutter) addGutterDim(editor, range) else null
  return Disposable {
    if (highlighter.isValid) highlighter.dispose()
    if (gutterDim != null) Disposer.dispose(gutterDim)
  }
}

/** Adds the dim to the gutter as a child. The gutter paints its children after its own content, so the dim covers the line numbers too. */
private fun addGutterDim(editor: Editor, range: TextRange?): Disposable? {
  val gutter = editor.gutter as? JComponent ?: return null
  val dim = StructuralPreviewGutterDim(editor, range)
  val resize = object : ComponentAdapter() {
    override fun componentResized(event: ComponentEvent) {
      dim.size = gutter.size
    }
  }
  dim.size = gutter.size
  gutter.add(dim, 0)
  gutter.addComponentListener(resize)
  gutter.repaint()
  return Disposable {
    gutter.removeComponentListener(resize)
    gutter.remove(dim)
    gutter.repaint()
  }
}

private fun hasBidirectionalBoundary(editor: Editor, range: TextRange): Boolean {
  val document = editor.document
  val unfolded = unfoldedRange(editor, range)
  val startLine = document.getLineNumber(unfolded.startOffset)
  val endLine = document.getLineNumber(unfolded.endOffset)
  return listOf(startLine, endLine).distinct().any { line ->
    val lineStart = document.getLineStartOffset(line)
    val content = document.charsSequence
    val text = CharArray(document.getLineEndOffset(line) - lineStart) { content[lineStart + it] }
    Bidi.requiresBidi(text, 0, text.size)
  }
}

/** Widens the range to the collapsed fold regions at its ends, so that a folded element counts as a whole. */
private fun unfoldedRange(editor: Editor, range: TextRange): TextRange {
  val folding = editor.foldingModel
  val startOffset = folding.getCollapsedRegionAtOffset(range.startOffset)?.startOffset ?: range.startOffset
  val lastOffset = maxOf(range.startOffset, range.endOffset - 1)
  val endOffset = folding.getCollapsedRegionAtOffset(lastOffset)?.endOffset ?: range.endOffset
  return TextRange(startOffset, endOffset)
}

private fun paintDim(editor: Editor, g: Graphics, dimmedArea: Area) {
  val graphics = g.create() as Graphics2D
  try {
    graphics.color = editor.colorsScheme.defaultBackground
    GraphicsUtil.paintWithAlpha(graphics, StructuralChangesViewSettings.previewDimAlpha()) { graphics.fill(dimmedArea) }
  }
  finally {
    graphics.dispose()
  }
}

private class StructuralPreviewDimRenderer(
  private val selectedRange: TextRange?,
  private val wholeLines: Boolean,
) : CustomHighlighterRenderer {
  override fun getOrder(): CustomHighlighterOrder = CustomHighlighterOrder.AFTER_TEXT

  override fun paint(editor: Editor, highlighter: RangeHighlighter, g: Graphics) {
    if (editor.isDisposed) return
    val selectedRange = selectedRange
    if (selectedRange != null && selectedRange.endOffset > editor.document.textLength) return
    val clip = g.clipBounds ?: editor.scrollingModel.visibleArea
    val dimmedArea = Area(clip)
    if (selectedRange != null) keepSelectedRange(editor, selectedRange, clip, dimmedArea)
    paintDim(editor, g, dimmedArea)
  }

  private fun keepSelectedRange(editor: Editor, selectedRange: TextRange, clip: Rectangle, dimmedArea: Area) {
    val unfolded = unfoldedRange(editor, selectedRange)
    val start = editor.offsetToXY(unfolded.startOffset, true, false)
    val end = editor.offsetToXY(unfolded.endOffset, false, true)
    val lineHeight = editor.lineHeight

    if (wholeLines) {
      dimmedArea.subtract(Area(Rectangle(clip.x, start.y, clip.width, end.y - start.y + lineHeight)))
    }
    else if (start.y == end.y) {
      dimmedArea.subtract(Area(Rectangle(minOf(start.x, end.x), start.y, kotlin.math.abs(end.x - start.x), lineHeight)))
    }
    else {
      dimmedArea.subtract(Area(Rectangle(start.x, start.y, (clip.x + clip.width - start.x).coerceAtLeast(0), lineHeight)))
      dimmedArea.subtract(Area(Rectangle(clip.x, start.y + lineHeight, clip.width, (end.y - start.y - lineHeight).coerceAtLeast(0))))
      dimmedArea.subtract(Area(Rectangle(clip.x, end.y, (end.x - clip.x).coerceAtLeast(0), lineHeight)))
    }
  }
}

/**
 * Dims the gutter outside the lines of the range. It paints after the gutter, so the dim covers the colors of the changes and the line
 * numbers. It takes no mouse events, so the gutter keeps its clicks and tooltips.
 */
private class StructuralPreviewGutterDim(private val editor: Editor, private val selectedRange: TextRange?) : JComponent() {
  init {
    isOpaque = false
  }

  override fun contains(x: Int, y: Int): Boolean = false

  override fun paintComponent(g: Graphics) {
    if (editor.isDisposed) return
    val selectedRange = selectedRange
    if (selectedRange != null && selectedRange.endOffset > editor.document.textLength) return
    val clip = g.clipBounds ?: Rectangle(0, 0, width, height)
    val dimmedArea = Area(clip)
    if (selectedRange != null) {
      val unfolded = unfoldedRange(editor, selectedRange)
      val top = editor.offsetToXY(unfolded.startOffset, true, false).y
      val bottom = editor.offsetToXY(unfolded.endOffset, false, true).y + editor.lineHeight
      dimmedArea.subtract(Area(Rectangle(clip.x, top, clip.width, bottom - top)))
    }
    paintDim(editor, g, dimmedArea)
  }
}
