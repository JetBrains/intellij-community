// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.caret.blink

import com.intellij.openapi.editor.impl.caret.model.CaretClock
import com.intellij.openapi.editor.impl.caret.model.CaretTick
import com.intellij.openapi.editor.impl.caret.model.TICK_MS
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

internal sealed interface CaretBlinkPhase {
  fun advance(tick: CaretTick): CaretBlinkPhase

  fun frame(tick: CaretTick, prefetching: Boolean): CaretBlinkFrame

  override fun toString(): String

  data object Dormant : CaretBlinkPhase {
    override fun advance(tick: CaretTick): CaretBlinkPhase = this

    override fun frame(tick: CaretTick, prefetching: Boolean): CaretBlinkFrame = CaretBlinkFrame.DORMANT
  }

  data object Awake : CaretBlinkPhase {
    override fun advance(tick: CaretTick): CaretBlinkPhase = when {
      !tick.settings.isBlinking || tick.isWithinQuietPeriod -> this
      tick.settings.blinksSmoothly -> Fading(from = 1.0, to = 0.0, startedAt = tick.now)
      else -> Toggling(visible = false, since = tick.now)
    }

    override fun frame(tick: CaretTick, prefetching: Boolean): CaretBlinkFrame = CaretBlinkFrame(
      opacity = 1.0f,
      nextDelay = if (tick.settings.isBlinking) tick.remainingQuietMs.milliseconds else Duration.INFINITE,
    )
  }

  data class Fading(private val from: Double, private val to: Double, private val startedAt: Long) : CaretBlinkPhase {
    override fun advance(tick: CaretTick): CaretBlinkPhase = when {
      tick.isInterrupted() -> Awake
      tick.elapsedSince(startedAt) < tick.settings.fadeDurationMs -> this
      else -> Holding(level = to, startedAt = tick.now)
    }

    override fun frame(tick: CaretTick, prefetching: Boolean): CaretBlinkFrame =
      CaretBlinkFrame(opacity = opacityAt(tick).toFloat(), nextDelay = CaretClock.TICK)

    private fun opacityAt(tick: CaretTick): Double {
      val t = (tick.elapsedSince(startedAt) / tick.settings.fadeDurationMs).coerceIn(0.0, 1.0)
      val progress = if (to < from) easeInOutCubic(t) else easeOutQuint(t)
      return from + (to - from) * progress
    }
  }

  data class Holding(private val level: Double, private val startedAt: Long) : CaretBlinkPhase {
    override fun advance(tick: CaretTick): CaretBlinkPhase = when {
      tick.isInterrupted() -> Awake
      tick.elapsedSince(startedAt) < tick.settings.holdDurationMs -> this
      else -> Fading(from = level, to = 1.0 - level, startedAt = tick.now)
    }

    override fun frame(tick: CaretTick, prefetching: Boolean): CaretBlinkFrame = CaretBlinkFrame(
      opacity = level.toFloat(),
      nextDelay = remainingMs(tick).milliseconds,
    )

    private fun remainingMs(tick: CaretTick): Long =
      (tick.settings.holdDurationMs.toLong() - tick.elapsedSince(startedAt).toLong()).coerceAtLeast(TICK_MS.toLong())
  }

  data class Toggling(private val visible: Boolean, private val since: Long) : CaretBlinkPhase {
    override fun advance(tick: CaretTick): CaretBlinkPhase = when {
      tick.isWithinQuietPeriod || !tick.settings.isBlinking || tick.settings.blinksSmoothly -> Awake
      tick.elapsedSince(since) < tick.settings.blinkPeriodMs -> this
      else -> Toggling(!visible, tick.now)
    }

    override fun frame(tick: CaretTick, prefetching: Boolean): CaretBlinkFrame = CaretBlinkFrame(
      opacity = if (visible) 1.0f else 0.0f,
      nextDelay = remainingMs(tick).milliseconds,
    )

    private fun remainingMs(tick: CaretTick): Long =
      (tick.settings.blinkPeriodMs - tick.elapsedSince(since).toLong()).coerceAtLeast(TICK_MS.toLong())
  }
}

private fun CaretTick.isInterrupted(): Boolean =
  isWithinQuietPeriod || !settings.isBlinking || !settings.blinksSmoothly

private fun easeOutQuint(t: Double): Double {
  val inv = 1 - t
  return 1 - inv * inv * inv * inv * inv
}

private fun easeInOutCubic(t: Double): Double =
  if (t < 0.5) 4 * t * t * t
  else 1 - (-2 * t + 2).let { it * it * it } / 2
