// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:Suppress("UndesirableClassUsage")

package com.intellij.util

import com.intellij.ui.paint.PaintUtil
import com.intellij.ui.scale.JBUIScale
import com.intellij.ui.scale.ScaleContext
import com.intellij.ui.scale.ScaleType
import com.intellij.util.ui.ImageUtil
import org.imgscalr.Scalr
import org.jetbrains.annotations.ApiStatus
import java.awt.Graphics2D
import java.awt.Image
import java.awt.image.BufferedImage
import java.awt.image.ImageObserver
import kotlin.math.roundToInt

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

  @Deprecated("Use IconManager instead.")
  constructor(width: Int, height: Int, type: Int) : this(scale = JBUIScale.sysScale().toDouble(),
                                                         width = width.toDouble(),
                                                         height = height.toDouble(),
                                                         type = type,
                                                         roundingMode = PaintUtil.RoundingMode.FLOOR)

  protected constructor(scale: Double, width: Double, height: Double, type: Int, roundingMode: PaintUtil.RoundingMode) :
    super(roundingMode.round(width * scale), roundingMode.round(height * scale), type) {
    delegate = null
    userWidth = width
    userHeight = height
    this.scale = scale
  }

  @ApiStatus.ScheduledForRemoval
  @Deprecated("Use IconManager instead.")
  constructor(image: Image, width: Int, height: Int, type: Int) : this(image = image,
                                                                       width = width.toDouble(),
                                                                       height = height.toDouble(),
                                                                       type = type)

  /**
   * Creates a HiDPI-aware BufferedImage wrapper for the provided scaled raw image.
   * The wrapper image will represent the scaled raw image in user coordinate space.
   *
   * @param image the scaled raw image
   * @param width the width in user coordinate space
   * @param height the height in user coordinate space
   * @param type the type
   */
  protected constructor(image: Image, width: Double, height: Double, type: Int) : super(1, 1, type) // a dummy wrapper
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
   * @param scaleContext the scaled context
   * @param type the type
   */
  internal constructor(image: Image, scaleContext: ScaleContext, type: Int) : super(1, 1, type) // a dummy wrapper
  {
    delegate = image
    scale = scaleContext.getScale(ScaleType.SYS_SCALE)
    userWidth = delegate.getWidth(null) / scale
    userHeight = delegate.getHeight(null) / scale
  }

  constructor(image: Image, sysScale: Double) : super(1, 1, TYPE_INT_ARGB) {
    delegate = image
    scale = sysScale
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
    val w = (scaleFactor * getRealWidth()).toInt()
    val h = (scaleFactor * (delegate?.getHeight(null) ?: super.getHeight(null))).toInt()
    if (w <= 0 || h <= 0) return this
    val scaled: Image = Scalr.resize(ImageUtil.toBufferedImage(img), Scalr.Method.QUALITY, w, h)
    val newUserWidth = w / scale
    val newUserHeight = h / scale
    if (delegate != null) {
      return JBHiDPIScaledImage(image = scaled, width = newUserWidth, height = newUserHeight, type = type)
    }
    val newImg = JBHiDPIScaledImage(scale = scale,
                                    width = newUserWidth,
                                    height = newUserHeight,
                                    type = type,
                                    roundingMode = PaintUtil.RoundingMode.ROUND)
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
    val w = getUserWidth()
    val h = getUserHeight()
    if (w <= 0 || h <= 0 || w == targetUserWidth && h == targetUserHeight) return this
    val targetWidth = (targetUserWidth * scale).roundToInt()
    val targetHeight = (targetUserHeight * scale).roundToInt()
    val scaled: Image = Scalr.resize(ImageUtil.toBufferedImage(img), Scalr.Method.QUALITY, Scalr.Mode.FIT_EXACT, targetWidth, targetHeight)
    if (delegate != null) {
      return JBHiDPIScaledImage(image = scaled, width = targetUserWidth.toDouble(), height = targetUserHeight.toDouble(), type = type)
    }
    val newImg = JBHiDPIScaledImage(scale = scale,
                                    width = targetUserWidth.toDouble(),
                                    height = targetUserHeight.toDouble(),
                                    type = type,
                                    roundingMode = PaintUtil.RoundingMode.ROUND)
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
  override fun getWidth(): Int = getWidth(null)

  /**
   * Returns the height in user coordinate space for the image created as a wrapper,
   * and the real height for the image created as a scaled one.
   *
   * @return the height
   */
  override fun getHeight(): Int = getHeight(null)

  /**
   * Returns the width in user coordinate space for the image created as a wrapper,
   * and the real width for the image created as a scaled one.
   *
   * @return the width
   */
  override fun getWidth(observer: ImageObserver?): Int {
    return if (delegate == null) super.getWidth(observer) else userWidth.roundToInt()
  }

  /**
   * Returns the height in user coordinate space for the image created as a wrapper,
   * and the real height for the image created as a scaled one.
   *
   * @return the height
   */
  override fun getHeight(observer: ImageObserver?): Int {
    return if (delegate == null) super.getHeight(observer) else userHeight.roundToInt()
  }

  /**
   * Returns the width in user coordinate space.
   *
   * @return the width
   */
  fun getUserWidth(): Int {
    return if (delegate == null) (super.getWidth(null) / scale).roundToInt() else userWidth.roundToInt()
  }

  /**
   * Returns the height in user coordinate space.
   *
   * @return the height
   */
  fun getUserHeight(): Int {
    return if (delegate == null) (super.getHeight(null) / scale).roundToInt() else userHeight.roundToInt()
  }

  /**
   * Returns the real width.
   *
   * @return the width
   */
  fun getRealWidth(): Int {
    return delegate?.getWidth(null) ?: super.getWidth(null)
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
