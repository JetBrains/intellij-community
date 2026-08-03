// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.caret.model

import kotlin.math.ceil

private const val VISUAL_BLINK_PERIOD_FACTOR = 1.2

internal data class CaretAnimationSettings(
  val blinkPeriodMs: Long,
  val isBlinking: Boolean,
  val blinksSmoothly: Boolean,
  val easing: CaretEasing,
  val moveDurationMs: Double,
) {
  val quietPeriodMs: Long get() = blinkPeriodMs

  val fadeDurationMs: Double = VISUAL_BLINK_PERIOD_FACTOR * blinkPeriodMs / 2.0

  val holdDurationMs: Double get() = fadeDurationMs

  val moveTimeConstantMs: Double = easing.timeConstantMs(moveDurationMs)

  val easingFrameCount: Int = ceil(moveDurationMs / TICK_MS).toInt().coerceAtLeast(1)
}
