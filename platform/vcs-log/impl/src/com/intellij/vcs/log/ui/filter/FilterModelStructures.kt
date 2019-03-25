// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.ui.filter

import com.intellij.vcs.log.VcsLogBranchFilter
import com.intellij.vcs.log.VcsLogFilter
import com.intellij.vcs.log.VcsLogRangeFilter
import com.intellij.vcs.log.VcsLogRevisionFilter

data class FilterPair<F1 : VcsLogFilter, F2 : VcsLogFilter>(val filter1: F1?, val filter2: F2?)

internal data class BranchFilters(val branchFilter: VcsLogBranchFilter?,
                                  val revisionFilter: VcsLogRevisionFilter?,
                                  val rangeFilter: VcsLogRangeFilter?)