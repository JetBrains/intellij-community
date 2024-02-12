// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.ui.filter

import com.intellij.vcs.log.*

data class FilterPair<F1 : VcsLogFilter, F2 : VcsLogFilter>(val filter1: F1?, val filter2: F2?) {
  fun isEmpty() = filter1 == null && filter2 == null
}

data class BranchFilters(val branchFilter: VcsLogBranchFilter?,
                         val revisionFilter: VcsLogRevisionFilter?,
                         val rangeFilter: VcsLogRangeFilter?) {
  fun isEmpty() = branchFilter == null && revisionFilter == null && rangeFilter == null

  companion object {
    fun fromCollection(collection: VcsLogFilterCollection): BranchFilters? {
      val branchFilters = BranchFilters(collection.get(VcsLogFilterCollection.BRANCH_FILTER),
                                        collection.get(VcsLogFilterCollection.REVISION_FILTER),
                                        collection.get(VcsLogFilterCollection.RANGE_FILTER))
      if (branchFilters.isEmpty()) return null
      return branchFilters
    }
  }
}