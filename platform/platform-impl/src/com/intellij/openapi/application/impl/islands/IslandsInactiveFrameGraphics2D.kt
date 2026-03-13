// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application.impl.islands

import com.intellij.ui.ColorUtil
import com.intellij.ui.Graphics2DDelegate
import com.intellij.ui.IslandsState
import com.intellij.ui.JBColor
import com.intellij.util.system.OS
import java.awt.AlphaComposite
import java.awt.Color
import java.awt.Component
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Image
import java.awt.Polygon
import java.awt.Shape
import java.awt.image.BufferedImage
import java.awt.image.BufferedImageOp
import java.awt.image.ImageObserver
import java.text.AttributedCharacterIterator
import javax.swing.SwingUtilities

/**
 * Paints everything with alpha [islandsInactiveAlpha] when the frame is not active
 */
internal class IslandsInactiveFrameGraphics2D(g: Graphics2D, private val component: Component) : Graphics2DDelegate(g) {

  var preserveComposite: Boolean = false

  private fun getAlpha(): Float {
    return if (SwingUtilities.getWindowAncestor(component)?.isActive == false) islandsInactiveAlpha else 1f
  }

  private fun shouldSkipAlphaBlending(alpha: Float): Boolean {
    return alpha == 1f || preserveComposite || isIslandsGradientColor(paint)
  }

  private fun <R> wrapPaint(runnable: () -> R): R {
    val alpha = getAlpha()

    if (shouldSkipAlphaBlending(alpha)) {
      return runnable.invoke()
    }

    val composite = getComposite()
    try {
      when (composite) {
        is AlphaComposite -> {
          setComposite(AlphaComposite.getInstance(composite.getRule(), composite.alpha * alpha))
        }
        null -> {
          setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha))
        }
      }

      return runnable.invoke()
    }
    finally {
      setComposite(composite)
    }
  }

  private fun <R> wrapPaintText(runnable: () -> R): R {
    val alpha = getAlpha()

    if (shouldSkipAlphaBlending(alpha)) {
      return runnable.invoke()
    }

    // todo Workaround of JBR-9949, should be removed when the bug is fixed
    // IJPL-230775: Use alpha blending for text on macOS Islands Light at high zoom
    // to avoid inconsistent dimming in inactive state
    val useAlphaBlending = OS.CURRENT == OS.macOS && IslandsState.isEnabled() && JBColor.isBright()
    if (!useAlphaBlending) {
      return wrapPaint(runnable)
    }

    val originalColor = color
    try {
      val foreground = ColorUtil.withAlpha(originalColor, alpha.toDouble())
      val background = JBColor.namedColor("MainWindow.background", JBColor.PanelBackground)
      color = ColorUtil.alphaBlending(foreground, background)

      return runnable.invoke()
    }
    finally {
      color = originalColor
    }
  }

  override fun clearRect(x: Int, y: Int, width: Int, height: Int) {
    wrapPaint {
      super.clearRect(x, y, width, height)
    }
  }

  override fun fillRect(x: Int, y: Int, width: Int, height: Int) {
    wrapPaint {
      super.fillRect(x, y, width, height)
    }
  }

  override fun fillArc(x: Int, y: Int, width: Int, height: Int, startAngle: Int, arcAngle: Int) {
    wrapPaint {
      super.fillArc(x, y, width, height, startAngle, arcAngle)
    }
  }

  override fun fillOval(x: Int, y: Int, width: Int, height: Int) {
    wrapPaint {
      super.fillOval(x, y, width, height)
    }
  }

  override fun fillPolygon(xPoints: IntArray, yPoints: IntArray, nPoints: Int) {
    wrapPaint {
      super.fillPolygon(xPoints, yPoints, nPoints)
    }
  }

  override fun fillPolygon(s: Polygon) {
    wrapPaint {
      super.fillPolygon(s)
    }
  }

  override fun fillRoundRect(x: Int, y: Int, width: Int, height: Int, arcWidth: Int, arcHeight: Int) {
    wrapPaint {
      super.fillRoundRect(x, y, width, height, arcWidth, arcHeight)
    }
  }

  override fun fill(s: Shape) {
    wrapPaint {
      super.fill(s)
    }
  }

  override fun drawImage(img: BufferedImage, op: BufferedImageOp?, x: Int, y: Int) {
    wrapPaint {
      super.drawImage(img, op, x, y)
    }
  }

  override fun drawImage(img: Image?, x: Int, y: Int, width: Int, height: Int, observer: ImageObserver?): Boolean {
    return wrapPaint {
      super.drawImage(img, x, y, width, height, observer)
    }
  }

  override fun drawImage(img: Image?, x: Int, y: Int, width: Int, height: Int, c: Color?, observer: ImageObserver?): Boolean {
    return wrapPaint {
      super.drawImage(img, x, y, width, height, c, observer)
    }
  }

  override fun drawImage(img: Image, x: Int, y: Int, observer: ImageObserver?): Boolean {
    return wrapPaint {
      super.drawImage(img, x, y, observer)
    }
  }

  override fun drawImage(img: Image, x: Int, y: Int, c: Color?, observer: ImageObserver?): Boolean {
    return wrapPaint {
      super.drawImage(img, x, y, c, observer)
    }
  }

  override fun drawImage(
    img: Image?,
    dx1: Int,
    dy1: Int,
    dx2: Int,
    dy2: Int,
    sx1: Int,
    sy1: Int,
    sx2: Int,
    sy2: Int,
    observer: ImageObserver?,
  ): Boolean {
    return wrapPaint {
      super.drawImage(img, dx1, dy1, dx2, dy2, sx1, sy1, sx2, sy2, observer)
    }
  }

  override fun drawImage(
    img: Image?,
    dx1: Int,
    dy1: Int,
    dx2: Int,
    dy2: Int,
    sx1: Int,
    sy1: Int,
    sx2: Int,
    sy2: Int,
    c: Color?,
    observer: ImageObserver?,
  ): Boolean {
    return wrapPaint {
      super.drawImage(img, dx1, dy1, dx2, dy2, sx1, sy1, sx2, sy2, c, observer)
    }
  }

  override fun drawString(iterator: AttributedCharacterIterator?, x: Float, y: Float) {
    wrapPaintText {
      super.drawString(iterator, x, y)
    }
  }

  override fun drawString(iterator: AttributedCharacterIterator?, x: Int, y: Int) {
    wrapPaintText {
      super.drawString(iterator, x, y)
    }
  }

  override fun drawString(s: String?, x: Float, y: Float) {
    wrapPaintText {
      super.drawString(s, x, y)
    }
  }

  override fun drawString(str: String, x: Int, y: Int) {
    wrapPaintText {
      super.drawString(str, x, y)
    }
  }

  override fun create(): Graphics {
    return IslandsInactiveFrameGraphics2D(super.create() as Graphics2D, component)
  }
}
