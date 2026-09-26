// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.caret.model

import kotlin.math.cbrt
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.pow
import kotlin.time.Duration

internal enum class CaretEasing {
  SNAPPY {
    override fun apply(progress: Double): Double {
      val root = cbrt(progress)
      return 3 * root - 3 * root.pow(2) + progress
    }
  },

  GLIDING {
    override fun apply(progress: Double): Double {
      // Horner form of rounded Hermite + α, β approx of cubic-bezier(0.25,0.1,0.25,1.0); monotone on [0,1], max dev ≈ 0.0176.
      return progress * ((((-5.4 * progress + 17.6) * progress - 20.6) * progress + 9.0) * progress + 0.4)
    }
  };

  /**
   * Maps linear [progress] in `[0, 1]` to eased progress in `[0, 1]`.
   */
  abstract fun apply(progress: Double): Double

  /**
   * Time constant of the exponential approach that reaches [MATCHED_PROGRESS] at the same moment this curve does.
   */
  fun timeConstant(duration: Duration): Duration {
    val matchedAt = progressWhereMatched(duration)
    val matchedDuration = duration * matchedAt
    val timeConstant = matchedDuration / TIME_CONSTANTS_TO_MATCH
    return timeConstant.coerceAtLeast(CaretFrameInterval.MOVEMENT)
  }

  /**
   * Progress at which this curve first reaches [MATCHED_PROGRESS], sampled once per animation frame.
   */
  private fun progressWhereMatched(duration: Duration): Double {
    val durationInFrames = (duration / CaretFrameInterval.MOVEMENT).toInt()
    val steps = max(MIN_TIME_CONSTANT_STEPS, durationInFrames)
    val sampledProgress = (1..steps).map { step -> step.toDouble() / steps }
    val firstMatch = sampledProgress.firstOrNull { progress -> apply(progress) >= MATCHED_PROGRESS }
    return firstMatch ?: 1.0
  }

  companion object {
    /**
     * Progress at which an exponential approach is considered to have matched the easing curve.
     */
    private const val MATCHED_PROGRESS = 0.9

    /**
     * How many time constants an exponential approach needs to reach [MATCHED_PROGRESS].
     */
    private val TIME_CONSTANTS_TO_MATCH = -ln(1.0 - MATCHED_PROGRESS)

    private const val MIN_TIME_CONSTANT_STEPS = 8
  }
}
