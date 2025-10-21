package com.intellij.cce.workspace.filter

import com.intellij.cce.core.Lookup

class RemoveSuccessfulCacheHitsFilter() : LookupFilter {
  override fun shouldRemove(lookup: Lookup): Boolean =
    lookup.rawFilteredList.isEmpty() && lookup.suggestions.any { it.isRelevant }

  private val Lookup.rawFilteredList: List<String>
    get() = this.additionalInfo["raw_filtered"] as? List<String> ?: emptyList()

  override val filterType = LookupFilter.LookupFilterType.REMOVE_SUCCESSFUL_CACHE_HITS
}