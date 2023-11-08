// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.changes

import com.intellij.collaboration.util.RefComparisonChange
import com.intellij.diff.comparison.ComparisonManagerImpl.getInstanceImpl
import com.intellij.diff.comparison.iterables.DiffIterableUtil
import com.intellij.diff.tools.util.text.LineOffsetsUtil
import com.intellij.diff.util.DiffUserDataKeysEx
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diff.impl.patch.PatchHunkUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.ex.isValidRanges
import git4idea.GitContentRevision
import org.jetbrains.annotations.ApiStatus.Internal

/**
 * Represents a set of changes in a branch compared to some other branch via three-dot-diff (via merge base)
 * Changes can be queried by commit via [changesByCommits] or as a cumulative set [changes]
 * Changes in [changes] are changes between merge base and head
 * Changes in [changesByCommits] are changes between individual adjacent commits
 *
 * Actual parsed changes are stored in [patchesByChange]
 */
@Internal
interface GitBranchComparisonResult {
  val baseSha: String
  val mergeBaseSha: String
  val headSha: String

  val changes: List<RefComparisonChange>

  val commits: List<GitCommitShaWithPatches>
  val changesByCommits: Map<String, List<RefComparisonChange>>

  val patchesByChange: Map<RefComparisonChange, GitTextFilePatchWithHistory>
}

fun GitBranchComparisonResult.findCumulativeChange(commitSha: String, filePath: String): RefComparisonChange? {
  for (change in changes) {
    if (patchesByChange[change]?.contains(commitSha, filePath) == true) {
      return change
    }
  }
  return null
}

fun GitTextFilePatchWithHistory.getDiffComputer(): DiffUserDataKeysEx.DiffComputer? {
  if (patch.hunks.isEmpty()) {
    logger<GitBranchComparisonResult>().info("Empty diff in patch $patch")
    return null
  }
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

fun RefComparisonChange.createVcsChange(project: Project): Change {
  val beforeRevision = filePathBefore?.let { GitContentRevision.createRevision(it, revisionNumberBefore, project) }
  val afterRevision = filePathAfter?.let { GitContentRevision.createRevision(it, revisionNumberAfter, project) }
  return Change(beforeRevision, afterRevision)
}