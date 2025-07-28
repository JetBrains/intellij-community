// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.paint

import com.intellij.ui.paint.PaintUtil
import com.intellij.ui.paint.PaintUtil.ParityMode.EVEN
import com.intellij.ui.paint.PaintUtil.ParityMode.ODD
import com.intellij.ui.paint.PaintUtil.RoundingMode.FLOOR
import com.intellij.ui.paint.PaintUtil.RoundingMode.ROUND
import com.intellij.ui.scale.ScaleContext
import com.intellij.vcs.log.VcsLogHighlighter.VcsCommitStyle
import java.awt.Graphics2D
import java.awt.geom.Ellipse2D
import kotlin.math.floor

internal class HeadNodePainter(
  scaleContext: ScaleContext,
  selectedLineThickness: Float,
  lineThickness: Float,
  rowHeight: Int,
) {
  companion object {
    // Dimensions:
    // - All circles share center point at (7.75, 7.75)
    // - Outer circle: 12.5px diameter
    // - Outer circle: 6.25px radius
    // - Middle circle: 4.25px radius
    // - Inner circle: 2.25px radius
    private const val OUTER_DIAMETER = 12.5
    private const val OUTER_RADIUS_RATIO = 6.25
    private const val MIDDLE_RADIUS_RATIO = 4.25
    private const val INNER_RADIUS_RATIO = 2.25

    // Proportional ratios calculated from dimensions
    private const val MIDDLE_CIRCLE_RATIO = MIDDLE_RADIUS_RATIO / OUTER_RADIUS_RATIO
    private const val INNER_CIRCLE_RATIO = INNER_RADIUS_RATIO / OUTER_RADIUS_RATIO
  }

  private val outerRadiusAligned = PaintUtil.alignToInt(OUTER_DIAMETER / 2, scaleContext, FLOOR, ODD).floorToInt()

  private val outerDiameter = PaintUtil.alignToInt(2 * PaintParameters.getCircleRadius(outerRadiusAligned, rowHeight), scaleContext, ROUND, EVEN)
  private val selectedOuterCircleDiameter = outerDiameter + selectedLineThickness - lineThickness

  private val outerRadius = PaintUtil.alignToInt(outerDiameter / 2, scaleContext, FLOOR, null)
  private val selectedOuterCircleRadius = PaintUtil.alignToInt(selectedOuterCircleDiameter / 2, scaleContext, FLOOR, null)

  fun paint(g2: Graphics2D, x: Double, y: Double, isSelected: Boolean, commitStyle: VcsCommitStyle) {
    val nodeColor = g2.color
    val radius = if (isSelected) selectedOuterCircleRadius else outerRadius
    val diameter = if (isSelected) selectedOuterCircleDiameter else outerDiameter

    // Outer circle
    val outerCircle = Ellipse2D.Double(x - radius, y - radius, diameter, diameter)
    g2.color = nodeColor
    g2.fill(outerCircle)

    // Middle circle
    val middleRadius = radius * MIDDLE_CIRCLE_RATIO
    val middleDiameter = diameter * MIDDLE_CIRCLE_RATIO
    val middleCircle = Ellipse2D.Double(x - middleRadius, y - middleRadius, middleDiameter, middleDiameter)
    g2.color = commitStyle.background
    g2.fill(middleCircle)

    // Inner circle
    val innerRadius = radius * INNER_CIRCLE_RATIO
    val innerDiameter = diameter * INNER_CIRCLE_RATIO
    val innerCircle = Ellipse2D.Double(x - innerRadius, y - innerRadius, innerDiameter, innerDiameter)
    g2.color = nodeColor
    g2.fill(innerCircle)
  }
}

private fun Double.floorToInt(): Int = floor(this).toInt()
