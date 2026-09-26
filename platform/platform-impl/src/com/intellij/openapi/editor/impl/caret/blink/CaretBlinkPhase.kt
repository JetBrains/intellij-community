// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.caret.blink

import com.intellij.openapi.editor.impl.caret.model.CaretFrameInterval
import com.intellij.openapi.editor.impl.caret.model.CaretTick
import com.intellij.openapi.editor.impl.view.animation.AnimationTimeMark
import kotlin.time.Duration

internal sealed interface CaretBlinkPhase {
  fun advance(tick: CaretTick): CaretBlinkPhase
  fun step(tick: CaretTick, prefetching: Boolean): CaretBlinkStep

  override fun toString(): String

  data object Dormant : CaretBlinkPhase {
    override fun advance(tick: CaretTick): CaretBlinkPhase = this
    override fun step(tick: CaretTick, prefetching: Boolean): CaretBlinkStep = CaretBlinkStep.DORMANT
  }

  data object Awake : CaretBlinkPhase {
    override fun advance(tick: CaretTick): CaretBlinkPhase {
      val staysAwake = !tick.settings().isBlinking() || tick.isWithinQuietPeriod()
      return when {
        staysAwake -> this
        tick.settings().blinksSmoothly() -> Fading(fromOpacity = 1.0, toOpacity = 0.0, startedAt = tick.now())
        else -> Toggling(visible = false, since = tick.now())
      }
    }

    override fun step(tick: CaretTick, prefetching: Boolean): CaretBlinkStep {
      val nextDelay = if (tick.settings().isBlinking()) {
        tick.remainingQuietTime()
      } else {
        Duration.INFINITE
      }
      return CaretBlinkStep(opacity = 1.0f, wantsPrefetch = false, nextDelay = nextDelay)
    }
  }

  /**
   * Interpolates the opacity from [fromOpacity] to [toOpacity] over one fade duration.
   */
  data class Fading(
    private val fromOpacity: Double,
    private val toOpacity: Double,
    private val startedAt: AnimationTimeMark,
  ) : CaretBlinkPhase {
    override fun advance(tick: CaretTick): CaretBlinkPhase {
      val isFading = tick.elapsedSince(startedAt) < tick.settings().fadeDuration()
      return when {
        tick.isInterrupted() -> Awake
        isFading -> this
        else -> Holding(opacity = toOpacity, startedAt = tick.now())
      }
    }

    override fun step(tick: CaretTick, prefetching: Boolean): CaretBlinkStep {
      val opacity = opacityAt(tick).toFloat()
      return CaretBlinkStep(
        opacity = opacity,
        wantsPrefetch = prefetching,
        nextDelay = CaretFrameInterval.BLINK,
      )
    }

    private fun opacityAt(tick: CaretTick): Double {
      val elapsed = tick.elapsedSince(startedAt)
      val fadeProgress = (elapsed / tick.settings().fadeDuration()).coerceIn(0.0, 1.0)
      val isFadingOut = toOpacity < fromOpacity
      val easedProgress = if (isFadingOut) easeInOutCubic(fadeProgress) else easeOutQuint(fadeProgress)
      return fromOpacity + (toOpacity - fromOpacity) * easedProgress
    }
  }

  /**
   * Rests at [opacity] between two fades.
   */
  data class Holding(private val opacity: Double, private val startedAt: AnimationTimeMark) : CaretBlinkPhase {
    override fun advance(tick: CaretTick): CaretBlinkPhase {
      val isHolding = tick.elapsedSince(startedAt) < tick.settings().holdDuration()
      return when {
        tick.isInterrupted() -> Awake
        isHolding -> this
        else -> Fading(fromOpacity = opacity, toOpacity = 1.0 - opacity, startedAt = tick.now())
      }
    }

    override fun step(tick: CaretTick, prefetching: Boolean): CaretBlinkStep {
      return CaretBlinkStep(
        opacity = opacity.toFloat(),
        wantsPrefetch = prefetching,
        nextDelay = remainingHoldDuration(tick),
      )
    }

    private fun remainingHoldDuration(tick: CaretTick): Duration {
      val remaining = tick.settings().holdDuration() - tick.elapsedSince(startedAt)
      return remaining.coerceAtLeast(CaretFrameInterval.BLINK)
    }
  }

  /**
   * Flips the caret between fully opaque and fully transparent once per half blink period.
   */
  data class Toggling(private val visible: Boolean, private val since: AnimationTimeMark) : CaretBlinkPhase {
    override fun advance(tick: CaretTick): CaretBlinkPhase {
      val wakesUp = tick.isWithinQuietPeriod() || !tick.settings().isBlinking() || tick.settings().blinksSmoothly()
      val keepsCurrentHalf = tick.elapsedSince(since) < tick.settings().blinkPeriod()
      return when {
        wakesUp -> Awake
        keepsCurrentHalf -> this
        else -> Toggling(visible = !visible, since = tick.now())
      }
    }

    override fun step(tick: CaretTick, prefetching: Boolean): CaretBlinkStep {
      val opacity = if (visible) 1.0f else 0.0f
      return CaretBlinkStep(opacity = opacity, wantsPrefetch = false, nextDelay = remainingHalfPeriod(tick))
    }

    private fun remainingHalfPeriod(tick: CaretTick): Duration {
      val remaining = tick.settings().blinkPeriod() - tick.elapsedSince(since)
      return remaining.coerceAtLeast(CaretFrameInterval.BLINK)
    }
  }
}

private fun easeOutQuint(progress: Double): Double {
  val inverted = 1 - progress
  val invertedPow5 = inverted * inverted * inverted * inverted * inverted
  return 1 - invertedPow5
}

private fun easeInOutCubic(progress: Double): Double {
  if (progress < 0.5) {
    return 4 * progress * progress * progress
  }
  val inverted = -2 * progress + 2
  val invertedPow3 = inverted * inverted * inverted
  return 1 - invertedPow3 / 2
}
