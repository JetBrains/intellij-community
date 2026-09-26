// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.ui.html

import com.intellij.icons.AllIcons
import com.intellij.ui.Graphics2DDelegate
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Image
import java.awt.Rectangle
import java.awt.Shape
import java.awt.image.ImageObserver
import javax.swing.Icon
import javax.swing.text.Element
import kotlin.math.max

internal class FitToWidthImageView(element: Element) : DataUrlImageView(element) {
  private val preferredSpan = ImageViewPreferredSpan(this) { axis -> super.getPreferredSpan(axis) }

  override fun getLoadingImageIcon(): Icon =
    AllIcons.Process.Step_passive

  override fun getResizeWeight(axis: Int): Int =
    if (axis == X_AXIS) 1 else 0

  override fun getMaximumSpan(axis: Int): Float =
    getPreferredSpan(axis)

  override fun getPreferredSpan(axis: Int): Float = preferredSpan.get(axis)

  override fun paint(g: Graphics, a: Shape) {
    val targetRect = if ((a is Rectangle)) a else a.bounds
    val scalingGraphics: Graphics = object : Graphics2DDelegate(g as Graphics2D) {
      override fun drawImage(img: Image, x: Int, y: Int, width: Int, height: Int, observer: ImageObserver): Boolean {
        var paintWidth = width
        var paintHeight = height
        val maxWidth = max(0.0,
                           (targetRect.width - 2 * (x - targetRect.x)).toDouble()).toInt() // assuming left and right insets are the same
        val maxHeight = max(0.0,
                            (targetRect.height - 2 * (y - targetRect.y)).toDouble()).toInt() // assuming top and bottom insets are the same
        if (paintWidth > maxWidth) {
          paintHeight = paintHeight * maxWidth / paintWidth
          paintWidth = maxWidth
        }
        if (paintHeight > maxHeight) {
          paintWidth = paintWidth * maxHeight / paintHeight
          paintHeight = maxHeight
        }
        return super.drawImage(img, x, y, paintWidth, paintHeight, observer)
      }
    }
    super.paint(scalingGraphics, a)
  }
}