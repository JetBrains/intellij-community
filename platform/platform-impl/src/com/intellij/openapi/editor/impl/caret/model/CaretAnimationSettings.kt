// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.caret.model

import kotlin.math.ceil
import kotlin.time.Duration

internal data class CaretAnimationSettings(
  val blinkPeriod: Duration,
  val isBlinking: Boolean,
  val blinksSmoothly: Boolean,
  val easing: CaretEasing,
  val moveDuration: Duration,
) {
  val quietPeriod: Duration get() = blinkPeriod

  val fadeDuration: Duration = blinkPeriod * VISUAL_BLINK_PERIOD_FACTOR / 2

  val holdDuration: Duration get() = fadeDuration

  val moveTimeConstant: Duration = easing.timeConstant(moveDuration)

  /**
   * How many frames a whole move takes, and therefore how many frames it is worth prefetching.
   */
  val easingFrameCount: Int = frameCountOf(moveDuration)

  companion object {
    /**
     * A fade reads as slower than a hard toggle of the same length, so the visual half period is stretched by this.
     */
    private const val VISUAL_BLINK_PERIOD_FACTOR = 1.2

    private fun frameCountOf(duration: Duration): Int {
      val exactFrames = duration / CaretFrameInterval.MOVEMENT
      val wholeFrames = ceil(exactFrames).toInt()
      return wholeFrames.coerceAtLeast(1)
    }
  }
}
