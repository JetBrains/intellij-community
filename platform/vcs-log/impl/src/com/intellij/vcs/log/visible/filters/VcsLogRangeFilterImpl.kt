// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.visible.filters

import com.intellij.openapi.util.Comparing
import com.intellij.openapi.util.NlsSafe
import com.intellij.vcs.log.VcsLogRangeFilter
import com.intellij.vcs.log.VcsLogRangeFilter.RefRange
import com.intellij.vcs.log.util.VcsLogUtil

internal class VcsLogRangeFilterImpl(override val ranges: List<RefRange>) : VcsLogRangeFilter {

  override fun getTextPresentation(): Collection<String> {
    return ranges.map { (before, after) -> "$before..$after" }
  }

  @NlsSafe
  override fun getDisplayText(): String {
    return ranges.joinToString(", ") { (before, after) ->
      "${VcsLogUtil.getShortHash(before)}..${VcsLogUtil.getShortHash(after)}"
    }
  }

  override fun toString(): String {
    return displayText
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as VcsLogRangeFilterImpl

    return Comparing.haveEqualElements(ranges, other.ranges)
  }

  override fun hashCode(): Int {
    return Comparing.unorderedHashcode(ranges)
  }
}
