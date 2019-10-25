// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.comment

import com.intellij.diff.util.LineRange
import com.intellij.diff.util.Range
import com.intellij.diff.util.Side
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestReviewThread
import org.jetbrains.plugins.github.pullrequest.data.GHPRChangedFileLinesMapper

object GHPRCommentsUtil {
  fun mapThreadsToLines(linesMapper: GHPRChangedFileLinesMapper, threads: List<GHPullRequestReviewThread>)
    : Map<Pair<Side, Int>, List<GHPullRequestReviewThread>> {

    val map = mutableMapOf<Pair<Side, Int>, List<GHPullRequestReviewThread>>()
    val threadsByPosition = threads.groupBy { it.position }
    for ((position, threads) in threadsByPosition) {
      if (position == null) continue
      val location = linesMapper.findFileLocation(position) ?: continue
      map[location] = threads
    }
    return map
  }

  fun getLineRanges(ranges: List<Range>, side: Side): List<LineRange> {
    return ranges.map {
      val start = side.select(it.start1, it.start2)
      val end = side.select(it.end1, it.end2)
      LineRange(start, end)
    }
  }
}