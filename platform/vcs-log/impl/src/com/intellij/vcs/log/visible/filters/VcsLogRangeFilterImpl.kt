// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.visible.filters

import com.intellij.vcs.log.VcsLogRangeFilter
import com.intellij.vcs.log.VcsLogRangeFilter.RefRange

internal class VcsLogRangeFilterImpl(override val ranges: List<RefRange>) : VcsLogRangeFilter {

  override fun getTextPresentation(): Collection<String> {
    return ranges.map { (before, after) -> "$before..$after" }
  }

  override fun toString(): String {
    return presentation
  }
}
