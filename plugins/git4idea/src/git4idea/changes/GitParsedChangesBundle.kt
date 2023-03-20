// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.changes

import com.intellij.diff.comparison.ComparisonManagerImpl.getInstanceImpl
import com.intellij.diff.comparison.iterables.DiffIterableUtil
import com.intellij.diff.tools.util.text.LineOffsetsUtil
import com.intellij.diff.util.DiffUserDataKeysEx
import com.intellij.openapi.diff.impl.patch.PatchHunkUtil
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.ex.isValidRanges
import com.intellij.util.containers.HashingStrategy
import java.util.*

/**
 * Represents a set of changes in a branch compared to some other branch via three-dot-diff (via merge base)
 * Changes can be queried by commit via [changesByCommits] or as a cumulative set [changes]
 * Changes in [changes] are changes between merge base and head
 * Changes in [changesByCommits] are changes between individual adjacent commits
 *
 * Actual parsed changes are stored in [patchesByChange]
 */
interface GitParsedChangesBundle {
  val changes: List<Change>
  val changesByCommits: Map<String, Collection<Change>>

  val patchesByChange: Map<Change, GitTextFilePatchWithHistory>

  companion object {
    val REVISION_COMPARISON_HASHING_STRATEGY: HashingStrategy<Change> = object : HashingStrategy<Change> {
      override fun equals(o1: Change?, o2: Change?): Boolean {
        return o1 == o2 &&
               o1?.beforeRevision == o2?.beforeRevision &&
               o1?.afterRevision == o2?.afterRevision
      }

      override fun hashCode(change: Change?) = Objects.hash(change, change?.beforeRevision, change?.afterRevision)
    }
  }
}

fun Map<Change, GitTextFilePatchWithHistory>.findCumulativeChange(commitSha: String, filePath: String): Change? =
  entries.find {
    it.value.isCumulative && it.value.contains(commitSha, filePath)
  }?.key

fun GitTextFilePatchWithHistory.getDiffComputer(): DiffUserDataKeysEx.DiffComputer {
  val diffRanges = patch.hunks.map(PatchHunkUtil::getChangeOnlyRanges).flatten()
  return DiffUserDataKeysEx.DiffComputer { text1, text2, policy, innerChanges, indicator ->
    val comparisonManager = getInstanceImpl()
    val lineOffsets1 = LineOffsetsUtil.create(text1)
    val lineOffsets2 = LineOffsetsUtil.create(text2)

    if (!isValidRanges(text1, text2, lineOffsets1, lineOffsets2, diffRanges)) {
      // TODO: exception with attachments
      error("Invalid diff line ranges for ${patch.filePath} in ${patch.afterVersionId!!}")
    }
    val iterable = DiffIterableUtil.create(diffRanges, lineOffsets1.lineCount,
                                           lineOffsets2.lineCount)
    DiffIterableUtil.iterateAll(iterable).map {
      comparisonManager.compareLinesInner(it.first, text1, text2, lineOffsets1, lineOffsets2, policy, innerChanges,
                                          indicator)
    }.flatten()
  }
}