// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log

import com.intellij.vcs.log.VcsLogFilterCollection.RANGE_FILTER

/**
 * Filters the log by a range or several ranges of commits.
 *
 * A range represents a set of commits which are reachable from the end of the range, but not reachable from the start of the range.
 * A continuous ancestry list of commits is one simple case of the range. Another example is a difference between branches.
 */
interface VcsLogRangeFilter : VcsLogBranchLikeFilter {

  /**
   * The range between commits (or references pointing to the commits), represented by this filter.
   */
  val ranges: List<RefRange>

  /**
   * Text presentation for the filter (to display in filter popup).
   */
  fun getTextPresentation(): Collection<String>

  override fun getKey(): VcsLogFilterCollection.FilterKey<VcsLogRangeFilter> {
    return RANGE_FILTER
  }

  /**
   * Represents a range between two references pointing to the commits.
   *
   * The references are given as Strings and may be either names of [VcsRef]s or commit hashes.
   *
   * @param exclusiveRef the reference not containing commits of the range, which means that commits are not reachable from this ref.
   * @param inclusiveRef the reference containing commits of the range, which means that commits are reachable from this ref.
   */
  data class RefRange(val exclusiveRef: String, val inclusiveRef: String)

}
