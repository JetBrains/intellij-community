// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.caret.model

import kotlin.math.ceil
import kotlin.time.Duration

internal data class CaretSettings(
  private val blinkPeriod: Duration,
  private val isBlinking: Boolean,
  private val blinksSmoothly: Boolean,
  private val easing: CaretEasing,
  private val moveDuration: Duration,
) {
  private val fadeDuration: Duration = blinkPeriod * VISUAL_BLINK_PERIOD_FACTOR / 2
  private val moveTimeConstant: Duration = easing.timeConstant(moveDuration)
  private val easingFrameCount: Int = frameCountOf(moveDuration)

  fun blinkPeriod(): Duration {
    return blinkPeriod
  }

  fun isBlinking(): Boolean {
    return isBlinking
  }

  fun blinksSmoothly(): Boolean {
    return blinksSmoothly
  }

  fun easing(): CaretEasing {
    return easing
  }

  fun moveDuration(): Duration {
    return moveDuration
  }

  /**
   * How many frames a whole move takes, and therefore how many frames it is worth prefetching.
   */
  fun easingFrameCount(): Int {
    return easingFrameCount
  }

  fun fadeDuration(): Duration {
    return fadeDuration
  }

  fun moveTimeConstant(): Duration {
    return moveTimeConstant
  }

  fun quietPeriod(): Duration {
    return blinkPeriod
  }

  fun holdDuration(): Duration {
    return fadeDuration
  }

  private fun frameCountOf(duration: Duration): Int {
    val exactFrames = duration / CaretFrameInterval.MOVEMENT
    val wholeFrames = ceil(exactFrames).toInt()
    return wholeFrames.coerceAtLeast(1)
  }

  companion object {
    /**
     * A fade reads as slower than a hard toggle of the same length, so the visual half period is stretched by this.
     */
    private const val VISUAL_BLINK_PERIOD_FACTOR = 1.2
  }
}
