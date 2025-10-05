// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.ui.comment

import com.intellij.diff.util.Side
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.markup.CustomHighlighterRenderer
import com.intellij.openapi.editor.markup.LineMarkerRenderer
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.ui.JBColor
import com.intellij.ui.scale.JBUIScale
import java.awt.*
import java.awt.geom.Path2D
import java.awt.geom.Path2D.WIND_NON_ZERO

class CommentedCodeFrameRenderer(
  private val startLine: Int,
  private val endLine: Int,
  private val editorSide: Side?,
) : CustomHighlighterRenderer, LineMarkerRenderer {

  override fun paint(editor: Editor, highlighter: RangeHighlighter, g: Graphics) { // editor part
    val (startVisLine, endVisLine) = getVisualLines(editor)
    var x = 0f
    var width = editor.contentComponent.width.toFloat()
    val (y, height) = getYAxisValues(startVisLine, endVisLine, editor)
    if (editorSide == Side.LEFT) {
      x += scrollbarPadding
      val path = createLeftOutlinePath(x, y, width, height)
      drawOutlinePath(g, path)
    }
    else {
      width -= scrollbarPadding
      val path = createRightOutlinePath(x, y, width, height)
      drawOutlinePath(g, path)
    }
  }

  override fun paint(editor: Editor, g: Graphics, r: Rectangle) { // gutter part
    val (startVisLine, endVisLine) = getVisualLines(editor)
    val x = 1f
    val width = (editor as? EditorEx)?.gutterComponentEx?.width?.toFloat() ?: 0f
    val (y, height) = getYAxisValues(startVisLine, endVisLine, editor)
    val path = createLeftOutlinePath(x, y, width, height)
    drawOutlinePath(g, path)
  }

  private fun drawOutlinePath(g: Graphics, path: Path2D.Float) {
    val g2d = g.create() as? Graphics2D ?: return
    try {
      g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
      g2d.color = color
      g2d.stroke = stroke
      g2d.draw(path)
    }
    finally {
      g2d.dispose()
    }
  }

  private fun getVisualLines(editor: Editor): Pair<Int, Int> {
    val doc = editor.document
    val startVisLine = editor.offsetToVisualPosition(doc.getLineStartOffset(startLine)).line
    val endVisLine = editor.offsetToVisualPosition(doc.getLineEndOffset(endLine.coerceAtMost(editor.document.lineCount - 1))).line
    return Pair(startVisLine, endVisLine)
  }

  private fun getYAxisValues(startVisLine: Int, endVisLine: Int, editor: Editor): Pair<Float, Float> {
    val y = editor.visualLineToY(startVisLine).toFloat()
    val height = editor.lineHeight.toFloat() * (endVisLine - startVisLine + 1) + getInlaysHeightInRange(editor, startLine..endLine)
    return Pair(y, height)
  }

  private fun createLeftOutlinePath(x: Float, y: Float, width: Float, height: Float): Path2D.Float {
    return Path2D.Float(WIND_NON_ZERO).apply {
      moveTo(width, y)
      lineTo(x + radius, y)
      quadTo(x, y, x, y + radius)
      lineTo(x, y + height - radius)
      quadTo(x, y + height, x + radius, y + height)
      lineTo(width, y + height)
    }
  }

  private fun createRightOutlinePath(x: Float, y: Float, width: Float, height: Float): Path2D.Float {
    return Path2D.Float(WIND_NON_ZERO).apply {
      moveTo(x, y)
      lineTo(x + width - radius, y)
      quadTo(x + width, y, x + width, y + radius)
      lineTo(x + width, y + height - radius)
      quadTo(x + width, y + height, x + width - radius, y + height)
      lineTo(x, y + height)
    }
  }

  private fun getInlaysHeightInRange(editor: Editor, range: IntRange): Int {
    if (range.last == 0) return 0
    val startOffset = editor.document.getLineStartOffset(range.first)
    val endOffset = editor.document.getLineEndOffset(range.last - 1)
    val inlays = editor.inlayModel.getBlockElementsInRange(startOffset, endOffset)
    return inlays.sumOf { it.bounds?.height ?: 0 }
  }

  companion object {
    private val color: Color = JBColor(0x3574f0, 0x3574f0)
    private val stroke: Stroke = BasicStroke(JBUIScale.scale(1.5f))
    private val radius: Int get() = JBUIScale.scale(6)
    private val scrollbarPadding: Int get() = JBUIScale.scale(15)
  }
}