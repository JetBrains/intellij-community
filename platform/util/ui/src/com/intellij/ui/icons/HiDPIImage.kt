// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.icons

import com.intellij.ui.paint.PaintUtil
import com.intellij.ui.scale.JBUIScale
import com.intellij.ui.scale.ScaleContext
import com.intellij.ui.scale.ScaleType
import com.intellij.util.JBHiDPIScaledImage
import org.jetbrains.annotations.ApiStatus
import java.awt.Graphics2D
import java.awt.GraphicsConfiguration
import java.awt.Image

@ApiStatus.Internal
class HiDPIImage : JBHiDPIScaledImage {
  constructor(image: Image, width: Int, height: Int, type: Int) : super(image = image,
                                                                        width = width.toDouble(),
                                                                        height = height.toDouble(),
                                                                        type = type)

  constructor(image: Image, width: Double, height: Double, type: Int) : super(image = image,
                                                                        width = width,
                                                                        height = height,
                                                                        type = type)

  /**
   * Creates a scaled HiDPI-aware BufferedImage, targeting the graphics scale.
   *
   * @param g the graphics which provides the target scale
   * @param width the width in user coordinate space
   * @param height the height in user coordinate space
   * @param type the type
   * @param roundingMode the rounding mode
   */
  constructor(g: Graphics2D?, width: Double, height: Double, type: Int, roundingMode: PaintUtil.RoundingMode) : super(
    scale = JBUIScale.sysScale(g).toDouble(),
    width = width,
    height = height,
    type = type,
    roundingMode = roundingMode,
  )

  /**
   * Creates a scaled HiDPI-aware BufferedImage, targeting the graphics config.
   *
   * @param gc the graphics config which provides the target scale
   * @param width the width in user coordinate space
   * @param height the height in user coordinate space
   * @param type the type
   */
  constructor(gc: GraphicsConfiguration?,
              width: Double,
              height: Double,
              type: Int,
              roundingMode: PaintUtil.RoundingMode) : super(
    JBUIScale.sysScale(gc).toDouble(), width, height, type, roundingMode)

  constructor(gc: GraphicsConfiguration?, width: Int, height: Int, type: Int) :
    super(scale = JBUIScale.sysScale(gc = gc).toDouble(),
          width = width.toDouble(),
          height = height.toDouble(),
          type = type,
          roundingMode = PaintUtil.RoundingMode.FLOOR)

  constructor(scaleContext: ScaleContext?, width: Double, height: Double, type: Int, roundingMode: PaintUtil.RoundingMode) :
    super(scale = scaleContext?.getScale(ScaleType.SYS_SCALE) ?: JBUIScale.sysScale().toDouble(),
          width = width,
          height = height,
          type = type,
          roundingMode = roundingMode)

  constructor(scale: Double, width: Double, height: Double, type: Int, roundingMode: PaintUtil.RoundingMode) :
    super(scale = scale,
          width = width,
          height = height,
          type = type,
          roundingMode = roundingMode)
}
