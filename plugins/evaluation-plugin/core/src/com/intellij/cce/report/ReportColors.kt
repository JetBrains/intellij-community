package com.intellij.cce.report

import com.intellij.cce.core.Session
import com.intellij.cce.core.Suggestion
import com.intellij.cce.metric.SuggestionsComparator

interface ReportColors<T> {
  companion object {
    fun <T> getColor(session: Session?, colors: ReportColors<T>, lookupOrder: Int, comparator: SuggestionsComparator): T {
      if (session == null || session.lookups.size <= lookupOrder) return colors.absentLookupColor
      val suggestions = session.lookups[lookupOrder].suggestions
      fun Suggestion.match(): Boolean = comparator.accept(this, session.expectedText)

      return when {
        !suggestions.any { it.match() } -> colors.notFoundColor
        suggestions.first().match() -> colors.perfectSortingColor
        suggestions.take(colors.goodSortingThreshold).any { it.match() } -> colors.goodSortingColor
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