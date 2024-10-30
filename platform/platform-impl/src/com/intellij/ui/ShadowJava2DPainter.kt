// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui

import com.intellij.openapi.util.registry.Registry
import com.intellij.util.ui.JBInsets
import com.intellij.util.ui.JBUI
import org.jetbrains.annotations.ApiStatus
import java.awt.Color
import java.awt.GradientPaint
import java.awt.Graphics2D
import java.awt.Insets


private val DEF_INSETS = JBInsets(5)
private val DEF_COLOR = Gray._200.withAlpha(50)

/**
 * @author Alexander Lobas
 */
@ApiStatus.Internal
class ShadowJava2DPainter(private val uiKeyGroup: String, private val arc: Int, private val borderColor: Color? = null) {
  private var hideTopCorners = false
  private var hideBottomSide = false

  companion object {
    fun enabled(): Boolean = Registry.`is`("ide.java2d.shadowEnabled", true)

    fun getInsets(uiKeyGroup: String): Insets = JBUI.insets("${uiKeyGroup}.Shadow.borderInsets", DEF_INSETS)
  }

  init {
    if (arc < 0) {
      throw IllegalArgumentException("Arc cannot be negative, arc = $arc")
    }
  }

  fun hideSide(topCorners: Boolean, bottom: Boolean) {
    hideTopCorners = topCorners
    hideBottomSide = bottom
  }

  fun getInsets() = Companion.getInsets(uiKeyGroup)

  fun paintShadow(g: Graphics2D, x: Int, y: Int, width: Int, height: Int) {
    val insets = getInsets()
    val xxLeft = x + insets.left
    val yyTop = y + insets.top
    val xxRight = x + width - insets.right
    val yyBottom = y + height - insets.bottom
    val widthLR = width - insets.left - insets.right
    val heightTB = height - insets.top - insets.bottom

    if (arc > 0) {
      g.color = getColor("topLeft", "1")
      g.fillRect(x + insets.left, y + insets.top, arc, arc)

      g.color = getColor("topRight", "1")
      g.fillRect(xxRight - arc, y + insets.top, arc, arc)

      if (!hideBottomSide) {
        g.color = getColor("bottomRight", "1")
        g.fillRect(xxRight - arc, yyBottom - arc, arc, arc)

        g.color = getColor("bottomLeft", "1")
        g.fillRect(x + insets.left, yyBottom - arc, arc, arc)
      }
    }

    setGradient(g, "top", "0", "1", xxLeft, y, xxLeft, yyTop)
    g.fillRect(xxLeft, y, widthLR, insets.top)

    if (!hideBottomSide) {
      setGradient(g, "bottom", "1", "0", xxLeft, yyBottom, xxLeft, y + height)
      g.fillRect(xxLeft, yyBottom, widthLR, insets.bottom)
    }

    val offset = if (hideTopCorners) JBUI.scale(7) else 0
    setGradient(g, "left", "0", "1", x, yyTop + offset, xxLeft, yyTop + offset)
    g.fillRect(x, yyTop + offset, insets.left, heightTB - offset)

    setGradient(g, "right", "1", "0", xxRight, yyTop + offset, x + width, yyTop + offset)
    g.fillRect(xxRight, yyTop + offset, insets.right, heightTB - offset)

    if (!hideTopCorners) {
      drawCorner(g, "topLeft", x, y, insets.left, insets.top)
      drawCorner(g, "topRight", xxRight, y, insets.right, insets.top)
    }

    if (!hideBottomSide) {
      drawCorner(g, "bottomRight", xxRight, yyBottom, insets.right, insets.bottom)
      drawCorner(g, "bottomLeft", x, yyBottom, insets.left, insets.bottom)
    }

    if (borderColor != null) {
      val one = JBUI.scale(1)
      g.color = borderColor
      g.drawRect(xxLeft - one, yyTop - one, widthLR + one, heightTB + one)
    }
  }

  private fun drawCorner(g: Graphics2D, side: String, x: Int, y: Int, width: Int, height: Int) {
    when (side) {
      "topLeft" -> setGradient(g, side, "0", "1", x + width / 2, y + height / 2, x + width, y + height)
      "topRight" -> setGradient(g, side, "0", "1", x + width / 2, y + height / 2, x, y + height)
      "bottomRight" -> setGradient(g, side, "0", "1", x + width / 2, y + height / 2, x, y)
      "bottomLeft" -> setGradient(g, side, "0", "1", x + width / 2, y + height / 2, x + width, y)
    }
    g.fillRect(x, y, width, height)
  }

  private fun setGradient(g: Graphics2D, sideKey: String, gKey0: String, gKey1: String, x0: Int, y0: Int, x1: Int, y1: Int) {
    g.paint = GradientPaint(x0.toFloat(), y0.toFloat(), getColor(sideKey, gKey0), x1.toFloat(), y1.toFloat(), getColor(sideKey, gKey1))
  }

  private fun getColor(sideKey: String, gradientKey: String): Color {
    return JBColor.namedColor("${uiKeyGroup}.Shadow.${sideKey}${gradientKey}Color", DEF_COLOR)
  }
}