// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.ui.render

import java.awt.BasicStroke
import java.awt.Color
import java.awt.Graphics2D
import java.awt.geom.Ellipse2D
import java.awt.geom.Path2D

internal object TagPainter {
  private const val ICON_SIZE: Float = 16.0f

  /**
   * @see community/platform/icons/src/expui/vcs/currentBranch.svg
   */
  @JvmStatic
  @JvmOverloads
  fun paintTag(g2: Graphics2D, offset: Float, withDot: Boolean = false, background: Color, tagColor: Color, size: Int) {
    val scale = size / ICON_SIZE

    val outline = Path2D.Float(Path2D.WIND_EVEN_ODD).apply {
      move(offset, scale, 13.4495f, 7.5001f)
      line(offset, scale, 7.01028f, 13.9394f)
      curve(offset, scale, 6.81502f, 14.1347f, 6.49843f, 14.1347f, 6.30317f, 13.9394f)
      line(offset, scale, 2.06053f, 9.69679f)
      curve(offset, scale, 1.86527f, 9.50152f, 1.86527f, 9.18494f, 2.06053f, 8.98968f)
      line(offset, scale, 8.49978f, 2.55035f)
      curve(offset, scale, 8.61106f, 2.43907f, 8.76824f, 2.38667f, 8.92404f, 2.40893f)
      line(offset, scale, 12.6363f, 2.93926f)
      curve(offset, scale, 12.8563f, 2.97069f, 13.0292f, 3.14354f, 13.0606f, 3.36352f)
      line(offset, scale, 13.5909f, 7.07584f)
      curve(offset, scale, 13.6132f, 7.23163f, 13.5608f, 7.38882f, 13.4495f, 7.5001f)
      closePath()
    }

    with(g2) {
      stroke = BasicStroke(1f * scale, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
      color = background
      fill(outline)
      color = tagColor
      draw(outline)

      if (withDot) {
        fill(oval(offset, scale, 1f, 10.5f, 5.5f))
      }
    }
  }

  private fun Path2D.Float.line(x0: Float, s: Float, x: Float, y: Float) = lineTo(x * s + x0, y * s)

  private fun Path2D.Float.move(x0: Float, s: Float, x: Float, y: Float) = moveTo(x * s + x0, y * s)

  private fun Path2D.Float.curve(x0: Float, s: Float, x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float) =
    curveTo(x1 * s + x0, y1 * s,
            x2 * s + x0, y2 * s,
            x3 * s + x0, y3 * s)

  @Suppress("SameParameterValue")
  private fun oval(x0: Float, s: Float, r: Float, centerX: Float, centerY: Float): Ellipse2D.Float =
    Ellipse2D.Float((centerX - r) * s + x0, (centerY - r) * s, 2 * r * s, 2 * r * s)
}
