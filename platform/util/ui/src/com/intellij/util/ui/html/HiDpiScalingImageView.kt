// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.ui.html

import com.intellij.icons.AllIcons
import com.intellij.ui.paint.PaintUtil
import com.intellij.ui.scale.ScaleContext
import com.intellij.ui.scale.ScaleType
import com.intellij.util.ui.ImageUtil
import com.intellij.util.ui.StartupUiUtil
import java.awt.Graphics
import java.awt.Rectangle
import java.awt.Shape
import java.awt.image.BufferedImage
import javax.swing.Icon
import javax.swing.text.Element
import javax.swing.text.html.ImageView

internal class HiDpiScalingImageView(elem: Element) : Base64ImageView(elem) {
  private val scaleContext: ScaleContext?
    get() = container?.takeIf(StartupUiUtil::isJreHiDPI)?.let(ScaleContext::create)

  private val sysScale: Float
    get() = scaleContext?.getScale(ScaleType.SYS_SCALE)?.toFloat() ?: 1f

  override fun getMaximumSpan(axis: Int): Float = super.getMaximumSpan(axis) / sysScale

  override fun getMinimumSpan(axis: Int): Float = super.getMinimumSpan(axis) / sysScale

  override fun getPreferredSpan(axis: Int): Float = super.getPreferredSpan(axis) / sysScale

  override fun getLoadingImageIcon(): Icon = AllIcons.Process.Step_passive

  override fun paint(g: Graphics, a: Shape) {
    val scaleContext = scaleContext
    if (scaleContext == null) {
      super.paint(g, a)
      return
    }

    val bounds = a.bounds
    val width = super.getPreferredSpan(X_AXIS).toDouble()
    val height = super.getPreferredSpan(Y_AXIS).toDouble()
    if (width <= 0 || height <= 0) return
    val image = ImageUtil.createImage(ScaleContext.createIdentity(), width, height,
                                      BufferedImage.TYPE_INT_ARGB,
                                      PaintUtil.RoundingMode.ROUND)
    val graphics = image.createGraphics()
    super.paint(graphics, Rectangle(image.width, image.height))
    StartupUiUtil.drawImage(g, ImageUtil.ensureHiDPI(image, scaleContext), bounds.x, bounds.y, null)
  }
}