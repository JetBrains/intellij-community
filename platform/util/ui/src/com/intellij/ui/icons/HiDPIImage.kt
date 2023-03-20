// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.icons

import com.intellij.ui.paint.PaintUtil
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.JBHiDPIScaledImage
import org.jetbrains.annotations.ApiStatus
import java.awt.Graphics2D
import java.awt.GraphicsConfiguration

@ApiStatus.Internal
class HiDPIImage : JBHiDPIScaledImage {
  constructor(width: Int, height: Int, type: Int) : super(width = width, height = height, type = type)

  /**
   * Creates a scaled HiDPI-aware BufferedImage, targeting the graphics scale.
   *
   * @param g the graphics which provides the target scale
   * @param width the width in user coordinate space
   * @param height the height in user coordinate space
   * @param type the type
   * @param rm the rounding mode
   */
  constructor(g: Graphics2D?, width: Double, height: Double, type: Int, rm: PaintUtil.RoundingMode) : super(
    g = g,
    width = width,
    height = height,
    type = type,
    rm = rm,
  )

  @JvmOverloads
  constructor(gc: GraphicsConfiguration?,
              width: Double,
              height: Double,
              type: Int,
              rm: PaintUtil.RoundingMode = PaintUtil.RoundingMode.FLOOR) : super(
    JBUIScale.sysScale(gc).toDouble(), width, height, type, rm)
}