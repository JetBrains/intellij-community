// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.caret.model

import com.intellij.openapi.editor.impl.view.animation.AnimationTimeMark
import kotlin.math.exp
import kotlin.math.pow
import kotlin.time.Duration

internal data class CaretTick(
  private val now: AnimationTimeMark,
  private val frameDuration: Duration,
  private val settings: CaretSettings,
  private val elapsedQuietTime: Duration,
) {
  fun isWithinQuietPeriod(): Boolean {
    return elapsedQuietTime < settings.quietPeriod()
  }

  fun now(): AnimationTimeMark {
    return now
  }

  fun settings(): CaretSettings {
    return settings
  }

  fun remainingQuietTime(): Duration {
    val remaining = settings.quietPeriod() - elapsedQuietTime
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

  /**
   * Whether a smooth blink in progress must give way to a fully opaque caret.
   */
  fun isInterrupted(): Boolean {
    val blinkingDisabled = !settings().isBlinking()
    val smoothBlinkingDisabled = !settings().blinksSmoothly()
    return isWithinQuietPeriod() || blinkingDisabled || smoothBlinkingDisabled
  }

  companion object {
    /**
     * Fraction of the inherited velocity that survives one [CaretFrameInterval.MOVEMENT].
     */
    private const val VELOCITY_DAMPING_PER_FRAME = 0.75
  }
}
