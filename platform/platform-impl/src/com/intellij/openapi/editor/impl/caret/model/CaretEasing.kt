// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.caret.model

import kotlin.math.cbrt
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.pow

private const val MATCHED_PROGRESS = 0.9

internal enum class CaretEasing {
  SNAPPY {
    override fun apply(t: Double): Double {
      val u = cbrt(t)
      return 3 * u - 3 * u.pow(2) + t
    }
  },

  GLIDING {
    override fun apply(t: Double): Double =
      // Horner form of rounded Hermite + α, β approx of cubic-bezier(0.25,0.1,0.25,1.0); monotone on [0,1], max dev ≈ 0.0176.
      t * ((((-5.4 * t + 17.6) * t - 20.6) * t + 9.0) * t + 0.4)
  };

  abstract fun apply(t: Double): Double

  internal fun timeConstantMs(durationMs: Double): Double {
    val steps = max(8, (durationMs / TICK_MS).toInt())
    val matchedAt = (1..steps)
      .map { it.toDouble() / steps }
      .firstOrNull { apply(it) >= MATCHED_PROGRESS }
      ?: 1.0

    return ((matchedAt * durationMs) / -ln(1.0 - MATCHED_PROGRESS)).coerceAtLeast(TICK_MS.toDouble())
  }
}
