// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.comment

import com.intellij.diff.util.LineRange
import com.intellij.diff.util.Range
import com.intellij.diff.util.Side

object GHPRCommentsUtil {

  fun getLineRanges(ranges: List<Range>, side: Side): List<LineRange> {
    return ranges.map {
      val start = side.select(it.start1, it.start2)
      val end = side.select(it.end1, it.end2)
      LineRange(start, end)
    }
  }
}