// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor.impl.skeleton.rendering

import com.intellij.util.animation.Easing
import kotlin.math.sin

/**
 * The skeleton timeline: the blocks fade in over [fadeInMs], and then they pulse with the period [pulseDurationMs].
 *
 * The fade starts at the background and ends at full contrast with zero speed, so the pulse starts at its peak without a jump.
 * The pulse runs only when [pulseEnabled] is true and [pulseDurationMs] is positive.
 * A [fadeInMs] of zero or less gives full contrast at once.
 */
internal class EditorSkeletonTimeline(
  private val fadeInMs: Long,
  pulseEnabled: Boolean,
  private val pulseDurationMs: Long,
) {
  private val pulseEnabled = pulseEnabled && pulseDurationMs > 0

  /**
   * Returns the state of the blocks at [elapsedMs] after the start of the fade-in.
   * The result depends only on [elapsedMs].
   */
  fun stateAt(elapsedMs: Long): EditorSkeletonFrameState {
    val fadeProgress = fadeInProgress(elapsedMs)
    if (fadeProgress < 1.0 || !pulseEnabled) {
      return EditorSkeletonFrameState(fadeProgress = fadeProgress, opacity = 1.0)
    }
    val pulseElapsedMs = elapsedMs - fadeInMs.coerceAtLeast(0)
    return EditorSkeletonFrameState(fadeProgress = 1.0, opacity = pulseOpacity(pulseElapsedMs))
  }

  private fun fadeInProgress(elapsedMs: Long): Double {
    if (fadeInMs <= 0) {
      return 1.0
    }
    return Easing.EASE_IN_OUT.calc(elapsedMs.coerceIn(0, fadeInMs).toDouble() / fadeInMs.toDouble())
  }

  private fun pulseOpacity(pulseElapsedMs: Long): Double {
    val phase = (pulseElapsedMs % pulseDurationMs).toDouble() / pulseDurationMs.toDouble()
    return PULSE_MIN_OPACITY + PULSE_AMPLITUDE * (sin(2 * Math.PI * (phase - PULSE_PHASE_SHIFT)) + 1)
  }
}

private const val PULSE_MIN_OPACITY = 0.3
private const val PULSE_AMPLITUDE = 0.35
private const val PULSE_PHASE_SHIFT = 0.75
