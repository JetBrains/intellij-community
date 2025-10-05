package com.intellij.cce.workspace.filter

import com.intellij.cce.core.Lookup

interface LookupFilter: NamedFilter {
  companion object {
    fun create(filterType: String): LookupFilter {
      return when (LookupFilterType.valueOf(filterType)) {
        LookupFilterType.REMOVE_SUCCESSFUL_CACHE_HITS -> RemoveSuccessfulCacheHitsFilter()
      }
    }
  }

  fun shouldRemove(lookup: Lookup): Boolean

  val filterType: LookupFilterType

  enum class LookupFilterType {
    REMOVE_SUCCESSFUL_CACHE_HITS,
  }

  override val name: String
    get() = filterType.name
}