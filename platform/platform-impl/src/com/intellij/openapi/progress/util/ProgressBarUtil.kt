// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.progress.util

import org.jetbrains.annotations.ApiStatus
import java.awt.Color
import java.awt.LinearGradientPaint
import java.awt.MultipleGradientPaint
import java.awt.Paint

object ProgressBarUtil {
  const val STATUS_KEY: String = "ProgressBar.status"

  const val PASSED_VALUE: String = "passed"
  const val WARNING_VALUE: String = "warning"
  const val FAILED_VALUE: String = "failed"

  /**
   * The paint used to draw the completed portion of the progress bar.
   * It should be configured for a horizontal layout with a progress width of 1.
   * Scaling and rotation are handled automatically.
   *
   * Value: [java.awt.Paint]
   */
  @ApiStatus.Internal
  const val PROGRESS_PAINT_KEY: String = "ProgressBar.progressPaint"

  /**
   * Creates a paint for the given colors. The first parameter is the quantity for correspondent color
   */
  @ApiStatus.Internal
  fun createMultiProgressPaint(quantityAndColors: List<Pair<Int, Color>>): Paint? {
    val minimalStep = 0.000001f
    val fullWeight = quantityAndColors.sumOf { it.first }.toFloat()
    val filtered = quantityAndColors.filter { it.first / fullWeight > minimalStep }
    if (filtered.isEmpty()) {
      return null
    }

    val fractions = mutableListOf<Float>()
    val colors = mutableListOf<Color>()
    var colorPos = 0f

    for ((quantity, color) in filtered) {
      val weight = quantity / fullWeight
      fractions += listOf(colorPos, colorPos + weight - minimalStep)
      colors += listOf(color, color)
      colorPos += weight
    }

    return LinearGradientPaint(0f, 0f, 1f, 0f, fractions.toFloatArray(), colors.toTypedArray(), MultipleGradientPaint.CycleMethod.NO_CYCLE)
  }
}
