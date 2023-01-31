// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.changes

import com.intellij.diff.comparison.ComparisonManagerImpl.getInstanceImpl
import com.intellij.diff.comparison.iterables.DiffIterableUtil
import com.intellij.diff.tools.util.text.LineOffsetsUtil
import com.intellij.diff.util.DiffUserDataKeysEx
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.ex.isValidRanges

/**
 * Represents a set of commits with their changes
 */
interface GitParsedChangesBundle {
  val changes: List<Change>
  val changesByCommits: Map<String, Collection<Change>>
  val linearHistory: Boolean

  fun findChangeDiffData(change: Change): GitChangeDiffData?

  fun findCumulativeChange(commitSha: String, filePath: String): Change?
}

fun GitChangeDiffData.getDiffComputer(): DiffUserDataKeysEx.DiffComputer {
  val diffRanges = diffRangesWithoutContext
  return DiffUserDataKeysEx.DiffComputer { text1, text2, policy, innerChanges, indicator ->
    val comparisonManager = getInstanceImpl()
    val lineOffsets1 = LineOffsetsUtil.create(text1)
    val lineOffsets2 = LineOffsetsUtil.create(text2)

    if (!isValidRanges(text1, text2, lineOffsets1, lineOffsets2, diffRanges)) {
      // TODO: exception with attachments
      error("Invalid diff line ranges for $filePath in $commitSha")
    }
    val iterable = DiffIterableUtil.create(diffRanges, lineOffsets1.lineCount,
                                           lineOffsets2.lineCount)
    DiffIterableUtil.iterateAll(iterable).map {
      comparisonManager.compareLinesInner(it.first, text1, text2, lineOffsets1, lineOffsets2, policy, innerChanges,
                                          indicator)
    }.flatten()
  }
}