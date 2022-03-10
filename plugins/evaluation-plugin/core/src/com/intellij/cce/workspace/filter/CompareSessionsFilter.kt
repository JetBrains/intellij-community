package com.intellij.cce.workspace.filter

import com.intellij.cce.core.Lookup

interface CompareSessionsFilter : NamedFilter {
  companion object {
    fun create(filterType: String, name: String, evaluationType: String): CompareSessionsFilter {
      return when (CompareFilterType.valueOf(filterType)) {
        CompareFilterType.POSITION_BETTER -> BetterPositionFilter(name, evaluationType)
        CompareFilterType.POSITION_WORSE -> WorsePositionFilter(name, evaluationType)
      }
    }
  }

  fun check(base: Lookup, forComparing: Lookup, text: String): Boolean
  val filterType: CompareFilterType
  val evaluationType: String

  enum class CompareFilterType {
    POSITION_BETTER,
    POSITION_WORSE
  }
}