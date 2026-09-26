// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.visible.filters

import com.intellij.vcs.log.VcsLogParentFilter

internal class VcsLogParentFilterImpl(override val minParents: Int = 0, override val maxParents: Int = Int.MAX_VALUE) : VcsLogParentFilter {
  @Suppress("HardCodedStringLiteral")
  override fun getDisplayText(): String {
    if (isNoMerges) return "--no-merges"
    return buildString {
      if (hasLowerBound) append("--min-parents=$minParents")
      if (hasUpperBound) append("--max-parents=$maxParents")
    }
  }
}

val VcsLogParentFilter.hasLowerBound get() = minParents > 0
val VcsLogParentFilter.hasUpperBound get() = maxParents != Int.MAX_VALUE

val VcsLogParentFilter.matchesAll: Boolean get() = minParents <= 0 && maxParents == Int.MAX_VALUE
val VcsLogParentFilter.isNoMerges: Boolean get() = maxParents == 1 && !hasLowerBound
