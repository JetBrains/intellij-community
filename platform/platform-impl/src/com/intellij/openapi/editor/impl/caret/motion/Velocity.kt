// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.caret.motion

import java.awt.geom.Point2D

internal class Velocity private constructor(
  private val dx: Double,
  private val dy: Double,
) {
  fun damped(factor: Double): Velocity {
    return Velocity(dx * factor, dy * factor)
  }

  fun stopped(alongX: Boolean, alongY: Boolean): Velocity {
    val nextDx = if (alongX) 0.0 else dx
    val nextDy = if (alongY) 0.0 else dy
    return Velocity(nextDx, nextDy)
  }

  /**
   * Carries [point] one tick further along this velocity.
   */
  fun appliedTo(point: Point2D): Point2D {
    return Point2D.Double(point.x + dx, point.y + dy)
  }

  override fun toString(): String {
    return "Velocity(dx=$dx, dy=$dy)"
  }

  companion object {
    val ZERO: Velocity = Velocity(0.0, 0.0)

    fun between(from: Point2D, to: Point2D): Velocity {
      return Velocity(to.x - from.x, to.y - from.y)
    }
  }
}
