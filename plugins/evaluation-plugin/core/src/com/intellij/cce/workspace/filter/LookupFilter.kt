package com.intellij.cce.workspace.filter

import com.intellij.cce.core.Lookup
import com.intellij.cce.workspace.filter.CompareSessionsFilter.CompareFilterType

interface LookupFilter: NamedFilter {
  companion object {
    fun create(filterType: String, name: String): RemoveSuccessfulCacheHitsFilter {
      return when (LookupFilterType.valueOf(filterType)) {
        LookupFilterType.REMOVE_SUCCESSFUL_CACHE_HITS -> RemoveSuccessfulCacheHitsFilter(name)
      }
    }
  }

  fun shouldRemove(lookup: Lookup): Boolean

  val filterType: LookupFilterType

  enum class LookupFilterType {
    REMOVE_SUCCESSFUL_CACHE_HITS,
  }
}