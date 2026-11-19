// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.view

import org.jetbrains.annotations.ApiStatus
import java.awt.geom.AffineTransform
import kotlin.math.ceil
import kotlin.math.floor

/**
 * The pixel grid of a device in the coordinates of a paint. The paint neither rotates nor shears.
 */
@ApiStatus.Internal
class DeviceGrid(
  private val scaleX: Double,
  private val translateX: Double,
  private val scaleY: Double,
  private val translateY: Double,
) {
  /**
   * The height of one device pixel.
   */
  val pixelHeight: Double = 1.0 / scaleY

  /**
   * Gives the pixel boundary nearest to [x].
   */
  fun alignX(x: Double): Double {
    return (floor(x * scaleX + translateX + 0.5) - translateX) / scaleX
  }

  /**
   * Gives the pixel boundary nearest to [y].
   */
  fun alignY(y: Double): Double {
    return (floor(y * scaleY + translateY + 0.5) - translateY) / scaleY
  }

  /**
   * Gives the shortest width of whole device pixels that is not shorter than [width].
   */
  fun wholePixelsX(width: Double): Double {
    return ceil(width * scaleX - EPSILON) / scaleX
  }

  /**
   * Gives the shortest height of whole device pixels that is not shorter than [height].
   */
  fun wholePixelsY(height: Double): Double {
    return ceil(height * scaleY - EPSILON) / scaleY
  }

  companion object {
    private const val EPSILON: Double = 1e-6

    /**
     * Gives the grid of [transform], or null when the transform rotates, shears or mirrors.
     */
    @JvmStatic
    fun of(transform: AffineTransform): DeviceGrid? {
      if (transform.shearX != 0.0 ||
          transform.shearY != 0.0 ||
          transform.scaleX <= 0.0 ||
          transform.scaleY <= 0.0) {
        return null
      }
      return DeviceGrid(transform.scaleX, transform.translateX, transform.scaleY, transform.translateY)
    }
  }
}
