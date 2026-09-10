// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.caret.model

import kotlin.math.ceil
import kotlin.time.Duration

private const val VISUAL_BLINK_PERIOD_FACTOR = 1.2

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

  val easingFrameCount: Int = ceil(moveDuration / CaretClock.TICK).toInt().coerceAtLeast(1)
}
