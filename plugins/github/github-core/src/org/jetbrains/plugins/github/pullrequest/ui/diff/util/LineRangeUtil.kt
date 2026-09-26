// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.ui.diff.util

import com.intellij.diff.util.LineRange
import kotlin.math.max
import kotlin.math.min

internal object LineRangeUtil {

  fun extract(
    leftRange: List<LineRange>,
    rightRange: List<LineRange>,
    leftChangedRanges: List<LineRange>,
    rightChangedRanges: List<LineRange>,
  ): List<LineRange> {
    val newLeftRanges = subtractRanges(leftRange, rightChangedRanges)
    val newRightRanges = subtractRanges(rightRange, leftChangedRanges)
    return sumRanges(newLeftRanges + newRightRanges)
  }

  private fun subtractRanges(source: List<LineRange>, subtract: List<LineRange>): List<LineRange> {
    var results = source
    for (subtractRange in subtract) {
      results = results.flatMap { currentRange ->
        val overlapStart = max(currentRange.start, subtractRange.start)
        val overlapEnd = min(currentRange.end, subtractRange.end)

        if (overlapStart >= overlapEnd) {
          listOf(currentRange)
        }
        else {
          val newRanges = mutableListOf<LineRange>()
          if (currentRange.start < overlapStart) {
            newRanges.add(LineRange(currentRange.start, overlapStart))
          }
          if (currentRange.end > overlapEnd) {
            newRanges.add(LineRange(overlapEnd, currentRange.end))
          }
          newRanges
        }
      }
    }
    return results
  }

  private fun sumRanges(ranges: List<LineRange>): List<LineRange> {
    val results = mutableListOf<LineRange>()
    val sortedRanges = ranges.sortedBy { it.start }
    results.add(sortedRanges.first())
    for (i in 1 until sortedRanges.size) {
      val current = results.last()
      val next = sortedRanges[i]

      val nextResult = if (next.start <= current.end) {
        results.removeAt(results.size - 1)
        LineRange(current.start, maxOf(current.end, next.end))
      }
      else next

      results.add(nextResult)
    }
    return results
  }
}