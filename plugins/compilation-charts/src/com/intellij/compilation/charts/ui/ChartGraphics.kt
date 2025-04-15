// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compilation.charts.ui

import com.intellij.ide.ui.UISettings.Companion.setupAntialiasing
import com.intellij.util.JBHiDPIScaledImage
import java.awt.Color
import java.awt.Font
import java.awt.FontMetrics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.Shape
import java.awt.Stroke
import java.awt.geom.AffineTransform
import java.awt.geom.Line2D
import java.awt.geom.Path2D
import java.awt.geom.Rectangle2D
import java.awt.image.BufferedImage
import java.awt.image.ImageObserver
import kotlin.math.roundToInt

class ChartGraphics(val graphics: Graphics2D, val offsetX: Double, val offsetY: Double) {
  constructor(graphics: Graphics2D, offsetX: Int, offsetY: Int) : this(graphics, offsetX.toDouble(), offsetY.toDouble())

  fun fill(s: Shape): Unit = graphics.fill(move(s))
  fun draw(s: Shape): Unit = graphics.draw(move(s))
  fun clip(s: Shape): Unit = graphics.clip(move(s))
  fun drawString(str: String, x: Float, y: Float): Unit = graphics.drawString(str, x + offsetX.toFloat(), y + offsetY.toFloat())
  fun drawString(str: String, x: Int, y: Int): Unit = graphics.drawString(str, x + offsetX.roundToInt(), y + offsetY.roundToInt())
  fun fillOval(x: Int, y: Int, width: Int, height: Int): Unit = graphics.fillOval(x + offsetX.roundToInt(), y + offsetY.roundToInt(), width, height)
  fun drawOval(x: Int, y: Int, width: Int, height: Int): Unit = graphics.drawOval(x + offsetX.roundToInt(), y + offsetY.roundToInt(), width, height)
  fun fontMetrics(): FontMetrics = graphics.fontMetrics
  fun create(): ChartGraphics = ChartGraphics(graphics.create() as Graphics2D, offsetX, offsetY)

  // helpers
  fun moveTo(offsetX: Double, offsetY: Double): ChartGraphics = ChartGraphics(graphics, -offsetX, -offsetY)

  fun withColor(color: Color, block: ChartGraphics.() -> Unit): ChartGraphics {
    val oldColor = graphics.color
    graphics.color = color
    block()
    graphics.color = oldColor
    return this
  }

  fun withFont(font: Font, block: ChartGraphics.() -> Unit): ChartGraphics {
    val oldFont = graphics.font
    graphics.font = font
    block()
    graphics.font = oldFont
    return this
  }

  fun withAntialiasing(block: ChartGraphics.() -> Unit): ChartGraphics {
    setupAntialiasing(graphics)

    val old = graphics.getRenderingHint(RenderingHints.KEY_ANTIALIASING)

    graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
    block()

    graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, old)
    return this
  }

  fun withStroke(stroke: Stroke, block: ChartGraphics.() -> Unit): ChartGraphics {
    val oldStroke = graphics.stroke
    graphics.stroke = stroke
    block()
    graphics.stroke = oldStroke
    return this
  }

  fun drawImage(img: BufferedImage, observer: ImageObserver?) {
    val transform = AffineTransform().apply {
      val scale = if (img is JBHiDPIScaledImage) 1 / img.scale else 1.0
      scale(scale, scale)
      translate(-offsetX / scale, -offsetY / scale)
    }
    graphics.drawImage(img, transform, observer)
  }

  fun withRenderingHints(): ChartGraphics {
    if (graphics.getRenderingHint(RenderingHints.KEY_ANTIALIASING) != RenderingHints.VALUE_ANTIALIAS_ON)
      graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
    if (graphics.getRenderingHint(RenderingHints.KEY_RENDERING) != RenderingHints.VALUE_RENDER_QUALITY)
      graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
    return this
  }

  fun getStringBounds(text: String): Rectangle2D = fontMetrics().getStringBounds(text, graphics)

  private fun move(s: Shape): Shape = when (s) {
    is Line2D -> move(s)
    is Rectangle2D -> move(s)
    is Path2D -> move(s)
    else -> s
  }

  private fun move(rect: Rectangle2D): Rectangle2D {
    if (offsetX != 0.0 || offsetY != 0.0) {
      return Rectangle2D.Double(rect.x + offsetX, rect.y + offsetY, rect.width, rect.height)
    }
    return rect
  }

  private fun move(path: Path2D): Path2D {
    if (offsetX != 0.0 || offsetY != 0.0) {
      return Path2D.Double(path, AffineTransform.getTranslateInstance(offsetX, offsetY))
    }
    return path
  }

  private fun move(line: Line2D): Line2D {
    if (offsetX != 0.0 || offsetY != 0.0) {
      return Line2D.Double(line.x1 + offsetX, line.y1 + offsetY, line.x2 + offsetX, line.y2 + offsetY)
    }
    else {
      return line
    }
  }
}