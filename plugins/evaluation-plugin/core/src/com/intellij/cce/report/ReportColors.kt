// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.cce.report

import com.intellij.cce.core.Session

interface ReportColors<T> {
  companion object {
    fun <T> getColor(session: Session?, colors: ReportColors<T>, lookupOrder: Int): T {
      if (session == null || session.lookups.size <= lookupOrder) return colors.absentLookupColor
      val suggestions = session.lookups[lookupOrder].suggestions

      return when {
        !suggestions.any { it.isSelected } -> colors.notFoundColor
        suggestions.first().isSelected -> colors.perfectSortingColor
        suggestions.take(colors.goodSortingThreshold).any { it.isSelected } -> colors.goodSortingColor
        else -> colors.badSortingColor
      }
    }
  }

  val perfectSortingColor: T
  val goodSortingColor: T
  val badSortingColor: T
  val notFoundColor: T
  val absentLookupColor: T
  val goodSortingThreshold: Int
}