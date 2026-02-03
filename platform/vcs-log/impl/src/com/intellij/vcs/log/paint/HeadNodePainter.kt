// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.paint

import com.intellij.ui.paint.PaintUtil
import com.intellij.ui.paint.PaintUtil.ParityMode.ODD
import com.intellij.ui.paint.PaintUtil.RoundingMode.FLOOR
import com.intellij.ui.scale.ScaleContext
import com.intellij.vcs.log.VcsLogHighlighter.VcsCommitStyle
import java.awt.Graphics2D
import java.awt.geom.Ellipse2D

internal class HeadNodePainter(
  scaleContext: ScaleContext,
  selectedLineThickness: Float,
  lineThickness: Float,
  rowHeight: Int,
) {
  companion object {
    private const val RADIUS_DELTA = 2
  }

  private val rawOuterCircleDiameter = 2 * PaintParameters.scaleWithRowHeight(PaintParameters.CIRCLE_RADIUS + RADIUS_DELTA, rowHeight)
  private val outerCircleDiameter = PaintUtil.alignToInt(rawOuterCircleDiameter, scaleContext, FLOOR, ODD)
  private val outerCircleRadius = PaintUtil.alignToInt(outerCircleDiameter / 2, scaleContext, FLOOR, null)

  private val selectedOuterCircleDiameter = outerCircleDiameter + selectedLineThickness - lineThickness
  private val selectedOuterCircleRadius = PaintUtil.alignToInt(selectedOuterCircleDiameter / 2, scaleContext, FLOOR, null)

  private val delta = PaintUtil.alignToInt(PaintParameters.scaleWithRowHeight(RADIUS_DELTA, rowHeight), scaleContext, FLOOR, null)

  fun paint(g2: Graphics2D, xCenter: Double, yCenter: Double, isSelected: Boolean, commitStyle: VcsCommitStyle) {
    if (isSelected) {
      g2.fillCircle(xCenter - selectedOuterCircleRadius, yCenter - selectedOuterCircleRadius, selectedOuterCircleDiameter)
      return
    }

    var x = xCenter - outerCircleRadius
    var y = yCenter - outerCircleRadius
    var diameter = outerCircleDiameter

    fun shrinkDiameter(delta: Double) {
      diameter -= delta * 2
      x += delta
      y += delta
    }

    // Outer circle
    g2.fillCircle(x, y, diameter)

    // Middle circle
    shrinkDiameter(delta)
    val nodeColor = g2.color
    g2.color = commitStyle.background
    g2.fillCircle(x, y, diameter)

    // Inner circle
    shrinkDiameter(delta)
    g2.color = nodeColor
    g2.fillCircle(x, y, diameter)
  }
}

private fun Graphics2D.fillCircle(x: Double, y: Double, diameter: Double) {
  val circle = Ellipse2D.Double(x, y, diameter, diameter)
  fill(circle)
}
