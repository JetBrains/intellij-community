// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.ui

import com.intellij.openapi.util.ScalableIcon
import com.intellij.ui.icons.CopyableIcon
import com.intellij.ui.scale.ScaleContext
import java.awt.Component
import java.awt.Graphics
import java.awt.Image
import javax.swing.Icon
import kotlin.math.roundToInt

data class JBHiDpiScalableIcon(
  val source: Image,
  private val height: Int = source.getHeight(null),
  private val width: Int = source.getWidth(null),
) : Icon, CopyableIcon, ScalableIcon {
  val scaledImage: Image by lazy {
    val hiDpi = ImageUtil.ensureHiDPI(source, ScaleContext.create())
    ImageUtil.scaleImage(hiDpi, height, width)
  }

  override fun paintIcon(c: Component?, g: Graphics, x: Int, y: Int) {
    StartupUiUtil.drawImage(g, scaledImage, x, y, observer = c)
  }

  override fun getIconWidth(): Int = width

  override fun getIconHeight(): Int = height

  override fun copy(): Icon = JBHiDpiScalableIcon(source, height, width)

  override fun getScale(): Float = height.toFloat() / source.getWidth(null)

  override fun scale(scaleFactor: Float): Icon =
    JBHiDpiScalableIcon(source, (height * scaleFactor).roundToInt(), (width * scaleFactor).roundToInt())

  override fun toString(): String = "HiDpiScaledIcon for $source"
}
