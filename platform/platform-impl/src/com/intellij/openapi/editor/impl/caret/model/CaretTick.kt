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

  val remainingQuietTime: Duration get() = (settings.quietPeriod - elapsedQuietTime).coerceAtLeast(CaretClock.MOVEMENT_FRAME)

  fun elapsedSince(startTime: AnimationTimeMark): Duration = (now - startTime).coerceAtLeast(Duration.ZERO)

  fun approachFactor(timeConstant: Duration): Double =
    (1.0 - exp(-frameDuration / timeConstant.coerceAtLeast(CaretClock.MOVEMENT_FRAME))).coerceIn(0.0, 1.0)

  fun velocityDamping(): Double = 0.75.pow(frameDuration / CaretClock.MOVEMENT_FRAME)
}
