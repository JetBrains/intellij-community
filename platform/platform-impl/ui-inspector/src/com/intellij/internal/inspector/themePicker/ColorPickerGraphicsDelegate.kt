// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.inspector.themePicker

import com.intellij.ui.Graphics2DDelegate
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.JBDimension
import com.intellij.util.ui.JBUI
import java.awt.*
import java.awt.font.GlyphVector
import java.awt.geom.AffineTransform
import java.awt.image.BufferedImage
import java.awt.image.BufferedImageOp
import java.awt.image.ImageObserver
import java.text.AttributedCharacterIterator
import javax.swing.JComponent
import kotlin.math.abs

internal class ColorPickerGraphicsDelegate private constructor(
  g: Graphics2D,
  val component: JComponent,
  private val offsets: Offsets,
) : Graphics2DDelegate(g) {
  // Approximate width and height
  private val charWidth = JBUI.scale(7)
  private val charHeight = JBUI.scale(15)
  private val placeholderSize = JBDimension(100, 15)

  override fun create(): Graphics {
    return ColorPickerGraphicsDelegate(delegate.create() as Graphics2D, component, offsets)
  }

  override fun clearRect(x: Int, y: Int, width: Int, height: Int) {
    super.clearRect(x, y, width, height)
    markUsedColor(x, y, width, height, color, erase = true)
  }

  override fun fillRect(x: Int, y: Int, width: Int, height: Int) {
    super.fillRect(x, y, width, height)
    markUsedColor(x, y, width, height, color, erase = true)
  }

  override fun fillArc(x: Int, y: Int, width: Int, height: Int, startAngle: Int, arcAngle: Int) {
    super.fillArc(x, y, width, height, startAngle, arcAngle)
    markUsedColor(x, y, width, height, color)
  }

  override fun fillOval(x: Int, y: Int, width: Int, height: Int) {
    super.fillOval(x, y, width, height)
    markUsedColor(x, y, width, height, color)
  }

  override fun fillPolygon(xPoints: IntArray, yPoints: IntArray, nPoints: Int) {
    super.fillPolygon(xPoints, yPoints, nPoints)
    val s = Polygon(xPoints, yPoints, nPoints)
    markUsedColor(s.bounds, color)
  }

  override fun fillPolygon(s: Polygon) {
    super.fillPolygon(s)
    markUsedColor(s.bounds, color)
  }

  override fun fillRoundRect(x: Int, y: Int, width: Int, height: Int, arcWidth: Int, arcHeight: Int) {
    super.fillRoundRect(x, y, width, height, arcWidth, arcHeight)
    markUsedColor(x, y, width, height, color)
  }

  override fun fill(s: Shape) {
    super.fill(s)
    markUsedColor(s.bounds, color)
  }

  override fun drawImage(img: BufferedImage, op: BufferedImageOp?, x: Int, y: Int) {
    super.drawImage(img, op, x, y)
    markUsedColor(x, y, img.width, img.height, img)
  }

  override fun drawImage(img: Image?, x: Int, y: Int, width: Int, height: Int, observer: ImageObserver?): Boolean {
    val b = super.drawImage(img, x, y, width, height, observer)
    markUsedColor(x, y, width, height, img)
    return b
  }

  override fun drawImage(img: Image?, x: Int, y: Int, width: Int, height: Int, c: Color?, observer: ImageObserver?): Boolean {
    val b = super.drawImage(img, x, y, width, height, c, observer)
    markUsedColor(x, y, width, height, img)
    return b
  }

  override fun drawImage(img: Image, x: Int, y: Int, observer: ImageObserver?): Boolean {
    val b = super.drawImage(img, x, y, observer)
    markUsedColor(x, y, img.getWidth(null), img.getHeight(null), img)
    return b
  }

  override fun drawImage(img: Image, x: Int, y: Int, c: Color?, observer: ImageObserver?): Boolean {
    val b = super.drawImage(img, x, y, c, observer)
    markUsedColor(x, y, img.getWidth(null), img.getHeight(null), img)
    return b
  }

  override fun drawImage(img: Image?, dx1: Int, dy1: Int, dx2: Int, dy2: Int, sx1: Int, sy1: Int, sx2: Int, sy2: Int, observer: ImageObserver?): Boolean {
    val b = super.drawImage(img, dx1, dy1, dx2, dy2, sx1, sy1, sx2, sy2, observer)
    markUsedColor(dx1, dy1, dx2 - dx1, dy2 - dy1, img)
    return b
  }

  override fun drawImage(img: Image?, dx1: Int, dy1: Int, dx2: Int, dy2: Int, sx1: Int, sy1: Int, sx2: Int, sy2: Int, c: Color?, observer: ImageObserver?): Boolean {
    val b = super.drawImage(img, dx1, dy1, dx2, dy2, sx1, sy1, sx2, sy2, c, observer)
    markUsedColor(dx1, dy1, dx2 - dx1, dy2 - dy1, img)
    return b
  }

  override fun drawString(str: String, x: Int, y: Int) {
    super.drawString(str, x, y)
    markUsedColor(x, y, str.length * charWidth, charHeight, color)
  }

  override fun drawString(str: String, x: Float, y: Float) {
    super.drawString(str, x, y)
    markUsedColor(x.toInt(), y.toInt(), str.length * charWidth, charHeight, color)
  }

  override fun drawString(iterator: AttributedCharacterIterator, x: Int, y: Int) {
    super.drawString(iterator, x, y)
    markUsedColor(x, y, placeholderSize.width, charHeight, color)
  }

  override fun drawString(iterator: AttributedCharacterIterator, x: Float, y: Float) {
    super.drawString(iterator, x, y)
    markUsedColor(x.toInt(), y.toInt(), placeholderSize.width, charHeight, color)
  }

  override fun drawChars(data: CharArray, offset: Int, length: Int, x: Int, y: Int) {
    super.drawChars(data, offset, length, x, y)
    markUsedColor(x, y, length * charWidth, charHeight, color)
  }

  override fun drawGlyphVector(g: GlyphVector, x: Float, y: Float) {
    super.drawGlyphVector(g, x, y)
    markUsedColor(x.toInt(), y.toInt(), g.numGlyphs * charWidth, charHeight, color)
  }

  override fun drawLine(x1: Int, y1: Int, x2: Int, y2: Int) {
    super.drawLine(x1, y1, x2, y2)
    val x = minOf(x1, x2)
    val y = minOf(y1, y2)
    val width = abs(x2 - x1).coerceAtLeast(1)
    val height = abs(y2 - y1).coerceAtLeast(1)
    markUsedColor(x, y, width, height, color)
  }

  override fun drawOval(x: Int, y: Int, width: Int, height: Int) {
    super.drawOval(x, y, width, height)
    markUsedColor(x, y, width, height, color)
  }

  override fun drawArc(x: Int, y: Int, width: Int, height: Int, startAngle: Int, arcAngle: Int) {
    super.drawArc(x, y, width, height, startAngle, arcAngle)
    markUsedColor(x, y, width, height, color)
  }

  override fun drawPolygon(xPoints: IntArray, yPoints: IntArray, nPoints: Int) {
    super.drawPolygon(xPoints, yPoints, nPoints)
    val s = Polygon(xPoints, yPoints, nPoints)
    markUsedColor(s.bounds, color)
  }

  override fun drawPolygon(s: Polygon) {
    super.drawPolygon(s)
    markUsedColor(s.bounds, color)
  }

  override fun drawPolyline(xPoints: IntArray, yPoints: IntArray, nPoints: Int) {
    super.drawPolyline(xPoints, yPoints, nPoints)
    val s = Polygon(xPoints, yPoints, nPoints)
    markUsedColor(s.bounds, color)
  }

  override fun drawRoundRect(x: Int, y: Int, width: Int, height: Int, arcWidth: Int, arcHeight: Int) {
    super.drawRoundRect(x, y, width, height, arcWidth, arcHeight)
    markUsedColor(x, y, width, height, color)
  }

  override fun draw(s: Shape) {
    super.draw(s)
    markUsedColor(s.bounds, color)
  }

  private fun markUsedColor(
    x: Int, y: Int, width: Int, height: Int, color: Any?,
    erase: Boolean = false,
  ) {
    val rectangle = Rectangle(x, y, width, height)
    markUsedColor(rectangle, color, erase)
  }

  /**
   * [Offsets.zero] is the location of the [component] top-left corner in the [Graphics2D] coordinate space
   * The graphics itself may belong to some unknown parent component.
   * We do assume that its transformations will consist of [com.intellij.ui.scale.ScaleType.SYS_SCALE] on JBR level and
   * translations while drawing [JComponent.paintChildren]
   *
   * We divide by [SYS_SCALE] to get back to the 'Swing' coordinate space, relative to our [component].
   *
   * [erase] - assume this is a background filling, erase all the old keys in the area
   *           If this ever becomes a problem - we may utilize [com.intellij.openapi.wm.IdeGlassPane] and [com.intellij.openapi.ui.Painter]
   *           as an ad-hoc 'repaint' listener.
   */
  private fun markUsedColor(drawRectangle: Rectangle, color: Any?, erase: Boolean = false) {
    val rectangle = drawRectangle.intersection(clipBounds)
    if (rectangle.width <= 0 || rectangle.height <= 0) return

    if (color !is Color) return

    val picker = UiThemeColorPicker.getInstance()
    val pixScale = JBUIScale.sysScale()

    val bounds = transform.createTransformedShape(rectangle).bounds

    bounds.x = ((bounds.x - offsets.zero.x) / pixScale).toInt()
    bounds.y = ((bounds.y - offsets.zero.y) / pixScale).toInt()
    bounds.width = (bounds.width / pixScale).toInt()
    bounds.height = (bounds.height / pixScale).toInt()
    picker.storeColorForPixel(component, bounds, color, erase)
  }

  companion object {
    fun wrap(g: Graphics2D, component: JComponent): Graphics2D {
      val gg = (g as? ColorPickerGraphicsDelegate)?.myDelegate ?: g
      val offsets = computeOffsets(gg, component) ?: return g
      return ColorPickerGraphicsDelegate(gg, component, offsets)
    }

    fun unwrap(g: Graphics2D): Graphics2D {
      return (g as? ColorPickerGraphicsDelegate)?.delegate ?: g
    }
  }
}

private fun computeOffsets(gg: Graphics2D, component: JComponent): Offsets? {
  val transformCopy = AffineTransform(gg.transform)
  val zero = Point(0, 0)
  transformCopy.transform(zero, zero)
  return Offsets(transformCopy, zero)
}

private data class Offsets(
  val transform: AffineTransform,
  val zero: Point,
)
