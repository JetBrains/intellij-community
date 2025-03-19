// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.coverage.view

import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
object PercentageParser {
  @JvmStatic
  fun parse(s: String): PercentageRecord {
    val percentIndex = s.indexOf('%')
    if (percentIndex < 0) return PercentageRecord.EMPTY
    val percent = safeParseDouble(s.substring(0, percentIndex))

    val openBraceIndex = s.indexOf('(', percentIndex)
    if (openBraceIndex < 0) return PercentageRecord(percent, null, null)
    val slashIndex = s.indexOf('/', openBraceIndex)
    if (slashIndex < 0) return PercentageRecord(percent, null, null)
    val closingBraceIndex = s.indexOf(')', slashIndex)
    if (closingBraceIndex < 0) return PercentageRecord(percent, null, null)

    val covered = safeParseDouble(s.substring(openBraceIndex + 1, slashIndex)).toInt()
    val total = safeParseDouble(s.substring(slashIndex + 1, closingBraceIndex)).toInt()
    return PercentageRecord(percent, covered, total)
  }

  @JvmStatic
  fun safeParseDouble(s: String): Double {
    try {
      return s.toDouble()
    }
    catch (e: NumberFormatException) {
      var begin = 0
      var end = -1
      var i = s.length
      while (--i >= 0) {
        val c = s[i]
        val isDigit = c in '0'..'9' || c == '.'
        if (isDigit) {
          if (end == -1) {
            end = i + 1
          }
          begin = i
        }
        else {
          if (end == -1) {
            continue
          }
          break
        }
      }
      if (end == -1) return 0.0
      return try {
        s.substring(begin, end).toDouble()
      }
      catch (e1: NumberFormatException) {
        0.0
      }
    }
  }
}

@ApiStatus.Internal
data class PercentageRecord(val percentage: Double?, val covered: Int?, val total: Int?): Comparable<PercentageRecord> {
  override fun compareTo(other: PercentageRecord): Int {
    if (percentage == null) return 1
    if (other.percentage == null) return -1
    val comparePercent = percentage.compareTo(other.percentage)
    if (comparePercent != 0) return comparePercent

    if (total == null) return 1
    if (other.total == null) return -1
    val compareTotal = total.compareTo(other.total)
    if (compareTotal != 0) return compareTotal

    if (covered == null) return 1
    if (other.covered == null) return -1
    return covered.compareTo(other.covered)
  }

  companion object {
    internal val EMPTY = PercentageRecord(null, null, null)
  }
}
