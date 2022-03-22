// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.ui.html

import com.intellij.ui.scale.ScaleContext
import com.intellij.ui.scale.ScaleType
import com.intellij.util.ui.ImageUtil
import com.intellij.util.ui.StartupUiUtil
import java.awt.Graphics
import java.awt.Rectangle
import java.awt.Shape
import java.awt.image.BufferedImage
import java.util.function.Supplier
import javax.swing.text.Element
import javax.swing.text.Position
import javax.swing.text.View
import javax.swing.text.html.ImageView

class HiDpiScalingImageView(elem: Element, private val originalView: ImageView) : View(elem) {

  private val scaleContext: ScaleContext?
    get() = container?.let(ScaleContext::create)

  private val sysScale: Float
    get() = scaleContext?.getScale(ScaleType.SYS_SCALE)?.toFloat() ?: 1f

  override fun getMaximumSpan(axis: Int): Float = originalView.getMaximumSpan(axis) / sysScale

  override fun getMinimumSpan(axis: Int): Float = originalView.getMinimumSpan(axis) / sysScale

  override fun getPreferredSpan(axis: Int): Float = originalView.getPreferredSpan(axis) / sysScale

  override fun paint(g: Graphics, a: Shape) {
    val scaleContext = scaleContext
    if (scaleContext == null) {
      originalView.paint(g, a)
      return
    }

    val bounds = a.bounds
    val width = originalView.getPreferredSpan(X_AXIS).toInt()
    val height = originalView.getPreferredSpan(Y_AXIS).toInt()
    if (width <= 0 || height <= 0) return
    val image = ImageUtil.createImage(width, height, BufferedImage.TYPE_INT_ARGB)
    val graphics = image.createGraphics()
    originalView.paint(graphics, Rectangle(image.width, image.height))
    StartupUiUtil.drawImage(g, ImageUtil.ensureHiDPI(image, scaleContext), bounds.x, bounds.y, null)
  }

  override fun modelToView(pos: Int, a: Shape?, b: Position.Bias?): Shape =
    originalView.modelToView(pos, a, b)

  override fun viewToModel(x: Float, y: Float, a: Shape?, biasReturn: Array<out Position.Bias>?): Int =
    originalView.viewToModel(x, y, a, biasReturn)

  override fun getToolTipText(x: Float, y: Float, allocation: Shape?): String =
    originalView.getToolTipText(x, y, allocation)
}