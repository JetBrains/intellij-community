// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.caret.model

import kotlin.math.exp
import kotlin.math.max
import kotlin.math.pow

internal data class CaretTick(
  val now: Long,
  private val frameMs: Double,
  val settings: CaretAnimationSettings,
  val isCaretShown: Boolean,
  private val quietMs: Long,
) {
  val isWithinQuietPeriod: Boolean get() = quietMs < settings.quietPeriodMs

  val remainingQuietMs: Long get() = (settings.quietPeriodMs - quietMs).coerceAtLeast(TICK_MS.toLong())

  fun elapsedSince(startMs: Long): Double = max(0L, now - startMs).toDouble()

  fun approachFactor(timeConstantMs: Double): Double =
    (1.0 - exp(-frameMs / max(TICK_MS.toDouble(), timeConstantMs))).coerceIn(0.0, 1.0)

  fun velocityDamping(): Double = 0.75.pow(frameMs / TICK_MS)
}
