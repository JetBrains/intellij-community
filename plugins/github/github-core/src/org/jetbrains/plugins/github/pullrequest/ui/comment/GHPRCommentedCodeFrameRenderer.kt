// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.ui.comment

import com.intellij.collaboration.ui.util.CodeReviewColorUtil
import com.intellij.diff.util.Side
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.markup.CustomHighlighterRenderer
import com.intellij.openapi.editor.markup.LineMarkerRenderer
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.ui.scale.JBUIScale
import org.jetbrains.plugins.github.pullrequest.ui.yRangeForLogicalLineRange
import java.awt.*
import java.awt.geom.Path2D
import java.awt.geom.Path2D.WIND_NON_ZERO

class CommentedCodeFrameRenderer(
  private val startLine: Int,
  private val endLine: Int,
  private val editorSide: Side?,
) : CustomHighlighterRenderer, LineMarkerRenderer {

  override fun paint(editor: Editor, highlighter: RangeHighlighter, g: Graphics) { // editor part
    var x = 0f
    var width = editor.contentComponent.width.toFloat()
    val (y, height) = editor.getYAxisValues()
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
    val x = OUTER_GUTTER_FRAME_PADDING
    val width = (editor as? EditorEx)?.gutterComponentEx?.width?.toFloat() ?: 0f
    val (y, height) = editor.getYAxisValues()
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

  private fun Editor.getYAxisValues(): Pair<Float, Float> {
    val yRange = yRangeForLogicalLineRange(startLine, endLine)
    val startY = yRange.first.toFloat()
    val height = yRange.last.toFloat() - startY
    return startY to height
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

  companion object {
    private const val OUTER_GUTTER_FRAME_PADDING = 2f
    private val color: Color = CodeReviewColorUtil.Review.LineFrame.border
    private val stroke: Stroke = BasicStroke(JBUIScale.scale(1f))
    private val radius: Int get() = JBUIScale.scale(4)
    private val scrollbarPadding: Int get() = JBUIScale.scale(15)
  }
}