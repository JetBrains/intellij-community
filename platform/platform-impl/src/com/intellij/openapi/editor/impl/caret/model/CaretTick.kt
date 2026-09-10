// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.caret.model

import kotlin.math.exp
import kotlin.math.pow
import kotlin.time.Duration

internal data class CaretTick(
  val now: CaretTimeMark,
  private val frameDuration: Duration,
  val settings: CaretAnimationSettings,
  val isCaretShown: Boolean,
  private val elapsedQuietTime: Duration,
) {
  val isWithinQuietPeriod: Boolean get() = elapsedQuietTime < settings.quietPeriod

  val remainingQuietTime: Duration get() = (settings.quietPeriod - elapsedQuietTime).coerceAtLeast(CaretClock.TICK)

  fun elapsedSince(startTime: CaretTimeMark): Duration = (now - startTime).coerceAtLeast(Duration.ZERO)

  fun approachFactor(timeConstant: Duration): Double =
    (1.0 - exp(-frameDuration / timeConstant.coerceAtLeast(CaretClock.TICK))).coerceIn(0.0, 1.0)

  fun velocityDamping(): Double = 0.75.pow(frameDuration / CaretClock.TICK)
}
