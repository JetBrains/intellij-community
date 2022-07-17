package com.intellij.cce.workspace.filter

import com.intellij.cce.core.Lookup

class WorsePositionFilter(name: String, evaluationType: String) : ComparePositionFilter(name, evaluationType) {
  override val filterType = CompareSessionsFilter.CompareFilterType.POSITION_WORSE

  override fun check(base: Lookup, forComparing: Lookup, text: String): Boolean {
    val basePosition = base.suggestions.indexOfFirst { text == it.text }
    val forComparingPosition = forComparing.suggestions.indexOfFirst { text == it.text }
    return upper(forComparingPosition, basePosition) || exist(forComparingPosition, basePosition)
  }
}