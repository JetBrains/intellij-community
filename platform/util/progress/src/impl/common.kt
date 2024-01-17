// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.util.progress.impl

internal data class FractionState<out T>(
  val fraction: Double,
  val state: T,
)

internal fun totalFraction(completed: Double, updates: Iterable<FractionState<*>>): Double {
  return if (updates.all { it.fraction < 0.0 }) {
    if (completed == 0.0) -1.0 else completed
  }
  else {
    completed + updates.sumOf { it.fraction.coerceAtLeast(0.0) }
  }
}

internal data class TextDetails(
  val text: ProgressText?,
  val details: ProgressText?,
) {
  companion object {
    val NULL: TextDetails = TextDetails(null, null)
  }
}

internal fun reduceText(states: List<ProgressText?>): ProgressText? {
  return states.firstNotNullOfOrNull { it }
}

internal fun reduceTextDetails(states: List<TextDetails>): TextDetails? {
  return states.firstOrNull { it.text != null }
         ?: states.firstOrNull { it.details != null }
         ?: states.firstOrNull()
}

const val ACCEPTABLE_FRACTION_OVERFLOW: Double = 1.0 / Int.MAX_VALUE

internal fun checkFraction(fraction: Double?): Boolean {
  if (fraction == null || fraction in 0.0..1.0) {
    return true
  }
  else {
    LOG.error(IllegalArgumentException("Fraction is expected to be `null` or a value in [0.0; 1.0], got $fraction"))
    return false
  }
}
