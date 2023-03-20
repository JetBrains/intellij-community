// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:Suppress("UndesirableClassUsage")

package com.intellij.util

import com.intellij.ui.paint.PaintUtil
import com.intellij.ui.scale.JBUIScale.sysScale
import com.intellij.ui.scale.ScaleContext
import com.intellij.ui.scale.ScaleType
import com.intellij.util.ui.ImageUtil
import org.imgscalr.Scalr
import org.jetbrains.annotations.ApiStatus
import java.awt.Graphics2D
import java.awt.GraphicsConfiguration
import java.awt.Image
import java.awt.image.BufferedImage
import java.awt.image.ImageObserver
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * @author Konstantin Bulenkov
 * @author tav
 */
@ApiStatus.Internal
@ApiStatus.NonExtendable
open class JBHiDPIScaledImage : BufferedImage {
  val delegate: Image?
  private val userWidth: Double
  private val userHeight: Double
  val scale: Double

  /**
   * @see .JBHiDPIScaledImage
   */
  constructor(width: Int, height: Int, type: Int) : this(width.toDouble(), height.toDouble(), type)

  /**
   * Creates a scaled HiDPI-aware BufferedImage, targeting the system default scale.
   *
   * @param width the width in user coordinate space
   * @param height the height in user coordinate space
   * @param type the type
   */
  constructor(width: Double, height: Double, type: Int) : this(gc = null as GraphicsConfiguration?,
                                                               width = width,
                                                               height = height,
                                                               type = type)

  /**
   * @see .JBHiDPIScaledImage
   */
  constructor(g: Graphics2D?, width: Int, height: Int, type: Int) : this(g = g,
                                                                         width = width.toDouble(),
                                                                         height = height.toDouble(),
                                                                         type = type,
                                                                         rm = PaintUtil.RoundingMode.FLOOR)

  /**
   * Creates a scaled HiDPI-aware BufferedImage, targeting the graphics scale.
   *
   * @param g the graphics which provides the target scale
   * @param width the width in user coordinate space
   * @param height the height in user coordinate space
   * @param type the type
   * @param rm the rounding mode
   */
  constructor(g: Graphics2D?, width: Double, height: Double, type: Int, rm: PaintUtil.RoundingMode) : this(
    scale = sysScale(g = g).toDouble(),
    width = width,
    height = height,
    type = type,
    rm = rm)

  /**
   * @see .JBHiDPIScaledImage
   */
  constructor(gc: GraphicsConfiguration?, width: Int, height: Int, type: Int) : this(gc = gc,
                                                                                     width = width.toDouble(),
                                                                                     height = height.toDouble(),
                                                                                     type = type)

  /**
   * @see .JBHiDPIScaledImage
   */
  constructor(ctx: ScaleContext?, width: Double, height: Double, type: Int, rm: PaintUtil.RoundingMode) : this(
    scale = sysScale(context = ctx),
    width = width,
    height = height,
    type = type,
    rm = rm)

  /**
   * Creates a scaled HiDPI-aware BufferedImage, targeting the graphics config.
   *
   * @param gc the graphics config which provides the target scale
   * @param width the width in user coordinate space
   * @param height the height in user coordinate space
   * @param type the type
   */
  @JvmOverloads
  constructor(gc: GraphicsConfiguration?,
              width: Double,
              height: Double,
              type: Int,
              rm: PaintUtil.RoundingMode = PaintUtil.RoundingMode.FLOOR) : this(
    sysScale(gc).toDouble(), width, height, type, rm)

  internal constructor(scale: Double, width: Double, height: Double, type: Int, rm: PaintUtil.RoundingMode) :
    super(rm.round(width * scale), rm.round(height * scale), type) {
    delegate = null
    userWidth = width
    userHeight = height
    this.scale = scale
  }

  /**
   * @see .JBHiDPIScaledImage
   */
  constructor(image: Image, width: Int, height: Int, type: Int) : this(image, width.toDouble(), height.toDouble(), type)

  /**
   * Creates a HiDPI-aware BufferedImage wrapper for the provided scaled raw image.
   * The wrapper image will represent the scaled raw image in user coordinate space.
   *
   * @param image the scaled raw image
   * @param width the width in user coordinate space
   * @param height the height in user coordinate space
   * @param type the type
   */
  constructor(image: Image, width: Double, height: Double, type: Int) : super(1, 1, type) // a dummy wrapper
  {
    delegate = image
    userWidth = width
    userHeight = height
    scale = if (userWidth > 0) delegate.getWidth(null) / userWidth else 1.0
  }

  /**
   * Creates a HiDPI-aware BufferedImage wrapper for the provided scaled raw image,
   * based on the provided ScaleContext.
   *
   * @param image the scaled raw image
   * @param ctx the scaled context
   * @param type the type
   */
  constructor(image: Image, ctx: ScaleContext, type: Int) : super(1, 1, type) // a dummy wrapper
  {
    delegate = image
    scale = ctx.getScale(ScaleType.SYS_SCALE)
    userWidth = delegate.getWidth(null) / scale
    userHeight = delegate.getHeight(null) / scale
  }

  /**
   * Returns JBHiDPIScaledImage of the same structure scaled by the provided factor.
   *
   * @param scaleFactor the scale factor
   * @return scaled instance
   */
  fun scale(scaleFactor: Double): JBHiDPIScaledImage {
    val img = delegate ?: this
    val w = (scaleFactor * getRealWidth(null)).toInt()
    val h = (scaleFactor * getRealHeight(null)).toInt()
    if (w <= 0 || h <= 0) return this
    val scaled: Image = Scalr.resize(ImageUtil.toBufferedImage(img), Scalr.Method.QUALITY, w, h)
    val newUserWidth = w / scale
    val newUserHeight = h / scale
    if (delegate != null) {
      return JBHiDPIScaledImage(scaled, newUserWidth, newUserHeight, type)
    }
    val newImg = JBHiDPIScaledImage(scale, newUserWidth, newUserHeight, type, PaintUtil.RoundingMode.ROUND)
    val g = newImg.createGraphics()
    g.drawImage(scaled, 0, 0, newUserWidth.roundToInt(), newUserHeight.roundToInt(),
                0, 0, scaled.getWidth(null), scaled.getHeight(null), null)
    g.dispose()
    return newImg
  }

  /**
   * Returns JBHiDPIScaledImage of the same structure scaled to the provided dimensions.
   * Dimensions are in user-space coordinates (unscaled)
   *
   * @return scaled instance
   */
  fun scale(targetUserWidth: Int, targetUserHeight: Int): JBHiDPIScaledImage {
    val img = delegate ?: this
    val w = getUserWidth(null)
    val h = getUserHeight(null)
    if (w <= 0 || h <= 0 || w == targetUserWidth && h == targetUserHeight) return this
    val targetWidth = (targetUserWidth * scale).roundToLong().toInt()
    val targetHeight = (targetUserHeight * scale).roundToLong().toInt()
    val scaled: Image = Scalr.resize(ImageUtil.toBufferedImage(img), Scalr.Method.QUALITY, Scalr.Mode.FIT_EXACT, targetWidth, targetHeight)
    if (delegate != null) {
      return JBHiDPIScaledImage(scaled, targetUserWidth, targetUserHeight, type)
    }
    val newImg = JBHiDPIScaledImage(scale, targetUserWidth.toDouble(), targetUserHeight.toDouble(), type, PaintUtil.RoundingMode.ROUND)
    val g = newImg.createGraphics()
    g.drawImage(scaled, 0, 0, targetUserWidth, targetUserHeight,
                0, 0, scaled.getWidth(null), scaled.getHeight(null), null)
    g.dispose()
    return newImg
  }

  /**
   * Returns the width in user coordinate space for the image created as a wrapper,
   * and the real width for the image created as a scaled one.
   *
   * @return the width
   */
  override fun getWidth(): Int {
    return getWidth(null)
  }

  /**
   * Returns the height in user coordinate space for the image created as a wrapper,
   * and the real height for the image created as a scaled one.
   *
   * @return the height
   */
  override fun getHeight(): Int {
    return getHeight(null)
  }

  /**
   * Returns the width in user coordinate space for the image created as a wrapper,
   * and the real width for the image created as a scaled one.
   *
   * @return the width
   */
  override fun getWidth(observer: ImageObserver?): Int {
    return if (delegate != null) getUserWidth(observer) else getRealWidth(observer)
  }

  /**
   * Returns the height in user coordinate space for the image created as a wrapper,
   * and the real height for the image created as a scaled one.
   *
   * @return the height
   */
  override fun getHeight(observer: ImageObserver?): Int {
    return if (delegate != null) getUserHeight(observer) else getRealHeight(observer)
  }

  /**
   * Returns the width in user coordinate space.
   *
   * @param observer the image observer
   * @return the width
   */
  fun getUserWidth(observer: ImageObserver?): Int {
    return if (delegate != null) userWidth.roundToInt() else (super.getWidth(observer) / scale).roundToLong().toInt()
  }

  /**
   * Returns the height in user coordinate space.
   *
   * @param observer the image observer
   * @return the height
   */
  fun getUserHeight(observer: ImageObserver?): Int {
    return if (delegate != null) userHeight.roundToInt() else (super.getHeight(observer) / scale).roundToLong().toInt()
  }

  /**
   * Returns the real width.
   *
   * @param observer the image observer
   * @return the width
   */
  fun getRealWidth(observer: ImageObserver?): Int {
    return delegate?.getWidth(observer) ?: super.getWidth(observer)
  }

  /**
   * Returns the real height.
   *
   * @param observer the image observer
   * @return the height
   */
  private fun getRealHeight(observer: ImageObserver?): Int {
    return delegate?.getHeight(observer) ?: super.getHeight(observer)
  }

  override fun createGraphics(): Graphics2D {
    val g = super.createGraphics()
    if (delegate == null) {
      g.scale(scale, scale)
      return HiDPIScaledGraphics(g)
    }
    return g
  }
}
