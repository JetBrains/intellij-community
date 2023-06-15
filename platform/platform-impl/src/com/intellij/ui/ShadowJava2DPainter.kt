// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui

import com.intellij.openapi.util.registry.Registry
import java.awt.*
import javax.swing.UIManager

/**
 * @author Alexander Lobas
 */
class ShadowJava2DPainter(private val uiKeyGroup: String, private val borderColor: Color? = null) {
  private var hideBottomSide = false

  companion object {
    fun enabled(): Boolean = Registry.`is`("ide.java2d.shadowEnabled", false)
  }

  fun hideBottomSide() {
    hideBottomSide = true
  }

  fun getInsets(): Insets = UIManager.getInsets("${uiKeyGroup}.Shadow.borderInsets")

  fun paintShadow(g: Graphics2D, x: Int, y: Int, width: Int, height: Int) {
    val insets = getInsets()
    val topLeftSize = UIManager.getDimension("${uiKeyGroup}.Shadow.topLeftSize")
    val topRightSize = UIManager.getDimension("${uiKeyGroup}.Shadow.topRightSize")
    val bottomLeftSize = UIManager.getDimension("${uiKeyGroup}.Shadow.bottomLeftSize")
    val bottomRightSize = UIManager.getDimension("${uiKeyGroup}.Shadow.bottomRightSize")

    setGradient(g, "top", 0, insets.top)
    g.fillRect(x + topLeftSize.width, y, width - topLeftSize.width - topRightSize.width, insets.top)

    if (!hideBottomSide) {
      setGradient(g, "bottom", 0, insets.bottom)
      g.fillRect(x + bottomLeftSize.width, y + height - insets.bottom, width - bottomLeftSize.width - bottomRightSize.width, insets.bottom)
    }

    setGradient(g, "left", insets.left, 0)
    g.fillRect(x, y + topLeftSize.height, insets.left, height - topLeftSize.height - bottomLeftSize.height)

    setGradient(g, "right", insets.right, 0)
    g.fillRect(x + width - insets.right, y + topRightSize.height, insets.right, height - topRightSize.height - bottomRightSize.height)

    val roundedCorners = UIManager.getBoolean("${uiKeyGroup}.Shadow.roundedCorners")

    if (roundedCorners) {
      paintArcCorner(g, x, y, topLeftSize, true, true, "topLeft")
      paintArcCorner(g, x + width - topRightSize.width, y, topRightSize, true, false, "topRight")
      if (!hideBottomSide) {
        paintArcCorner(g, x + width - bottomRightSize.width, y + height - bottomRightSize.height, bottomRightSize, false, false,
                       "bottomRight")
        paintArcCorner(g, x, y + height - bottomLeftSize.height, bottomLeftSize, false, true, "bottomLeft")
      }
    }
    else {
      paintPolygonCorner(g, x, y, width, height, insets, topLeftSize, true, true, "topLeft")
      paintPolygonCorner(g, x, y, width, height, insets, topRightSize, true, false, "topRight")
      if (!hideBottomSide) {
        paintPolygonCorner(g, x, y, width, height, insets, bottomRightSize, false, false, "bottomRight")
        paintPolygonCorner(g, x, y, width, height, insets, bottomLeftSize, false, true, "bottomLeft")
      }
    }

    if (borderColor != null) {
      g.color = borderColor
      g.drawRect(x + insets.left - 1, y + insets.top - 1, x + width - insets.left - insets.right + 1,
                 y + height - insets.top - insets.bottom + 1)
    }
  }

  private fun paintArcCorner(g: Graphics2D, x: Int, y: Int, size: Dimension, top: Boolean, left: Boolean, side: String) {
    val w2 = size.width * 2
    val h2 = size.height * 2

    setGradient(g, side, size.width, size.height)

    if (top && left) {
      g.fillArc(x, y, w2, h2, 90, 90)
    }
    else if (top/* && !left*/) {
      g.fillArc(x - size.width, y, w2, h2, 0, 90)
    }
    else if (/*!top && */!left) {
      g.fillArc(x - size.width, y - size.height, w2, h2, 0, -90)
    }
    else /*if (!top && left)*/ {
      g.fillArc(x, y - size.height, w2, h2, -90, -90)
    }
  }

  private fun paintPolygonCorner(g: Graphics2D,
                                 x: Int,
                                 y: Int,
                                 width: Int,
                                 height: Int,
                                 insets: Insets,
                                 sideSize: Dimension,
                                 top: Boolean,
                                 left: Boolean,
                                 side: String) {
    setGradient(g, side, sideSize.width, sideSize.height)

    val polygon = Polygon()

    if (top && left) {
      polygon.addPoint(x, y + sideSize.height)
      polygon.addPoint(x, y)
      polygon.addPoint(x + sideSize.width, y)
      polygon.addPoint(x + sideSize.width, y + insets.top)
      polygon.addPoint(x + insets.left, y + insets.top)
      polygon.addPoint(x + insets.left, y + sideSize.height)
      polygon.addPoint(x, y + sideSize.height)
    }
    else if (top/* && !left*/) {
      polygon.addPoint(x + width - sideSize.width, y)
      polygon.addPoint(x + width, y)
      polygon.addPoint(x + width, y + sideSize.height)
      polygon.addPoint(x + width - insets.right, y + sideSize.height)
      polygon.addPoint(x + width - insets.right, y + insets.top)
      polygon.addPoint(x + width - sideSize.width, y + insets.top)
      polygon.addPoint(x + width - sideSize.width, y)
    }
    else if (/*!top && */!left) {
      polygon.addPoint(x + width - sideSize.width, y + height - insets.bottom)
      polygon.addPoint(x + width - insets.right, y + height - insets.bottom)
      polygon.addPoint(x + width - insets.right, y + height - sideSize.height)
      polygon.addPoint(x + width, y + height - sideSize.height)
      polygon.addPoint(x + width, y + height)
      polygon.addPoint(x + width - sideSize.width, y + height)
      polygon.addPoint(x + width - sideSize.width, y + height - insets.bottom)
    }
    else /*if (!top && left)*/ {
      polygon.addPoint(x, y + height)
      polygon.addPoint(x, y + height - sideSize.height)
      polygon.addPoint(x + insets.left, y + height - sideSize.height)
      polygon.addPoint(x + insets.left, y + height - insets.bottom)
      polygon.addPoint(x + sideSize.width, y + height - insets.bottom)
      polygon.addPoint(x + sideSize.width, y + height)
      polygon.addPoint(x, y + height)
    }

    g.fillPolygon(polygon)
  }

  private fun setGradient(g: Graphics2D, sideKey: String, lengthX: Int, lengthY: Int) {
    g.paint = GradientPaint(0f, 0f, getColor(sideKey, "0"), lengthX.toFloat(), lengthY.toFloat(), getColor(sideKey, "1"))
  }

  private fun getColor(sideKey: String, gradientKey: String): Color {
    return UIManager.getColor("${uiKeyGroup}.Shadow.${sideKey}${gradientKey}Color")
  }
}