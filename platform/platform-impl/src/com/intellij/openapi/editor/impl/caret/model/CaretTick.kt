// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.caret.model

import com.intellij.openapi.editor.impl.view.animation.AnimationTimeMark
import kotlin.math.exp
import kotlin.math.pow
import kotlin.time.Duration

internal data class CaretTick(
  val now: AnimationTimeMark,
  private val frameDuration: Duration,
  val settings: CaretAnimationSettings,
  private val elapsedQuietTime: Duration,
) {
  val isWithinQuietPeriod: Boolean get() = elapsedQuietTime < settings.quietPeriod

  val remainingQuietTime: Duration
    get() {
      val remaining = settings.quietPeriod - elapsedQuietTime
      return remaining.coerceAtLeast(CaretFrameInterval.MOVEMENT)
    }

  fun elapsedSince(startTime: AnimationTimeMark): Duration {
    val elapsed = now - startTime
    return elapsed.coerceAtLeast(Duration.ZERO)
  }

  /**
   * Fraction of the remaining distance to close during this tick, for an exponential approach with [timeConstant].
   */
  fun approachFactor(timeConstant: Duration): Double {
    val effectiveTimeConstant = timeConstant.coerceAtLeast(CaretFrameInterval.MOVEMENT)
    val elapsedTimeConstants = frameDuration / effectiveTimeConstant
    val closedFraction = 1.0 - exp(-elapsedTimeConstants)
    return closedFraction.coerceIn(0.0, 1.0)
  }

  fun velocityDamping(): Double {
    val elapsedFrames = frameDuration / CaretFrameInterval.MOVEMENT
    return VELOCITY_DAMPING_PER_FRAME.pow(elapsedFrames)
  }

  companion object {
    /**
     * Fraction of the inherited velocity that survives one [CaretFrameInterval.MOVEMENT].
     */
    private const val VELOCITY_DAMPING_PER_FRAME = 0.75
  }
}
