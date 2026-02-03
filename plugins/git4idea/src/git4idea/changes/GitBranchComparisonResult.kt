// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.changes

import com.intellij.collaboration.util.RefComparisonChange
import com.intellij.diff.comparison.ComparisonManagerImpl.getInstanceImpl
import com.intellij.diff.comparison.iterables.DiffIterableUtil
import com.intellij.diff.tools.util.text.LineOffsetsUtil
import com.intellij.diff.util.DiffUserDataKeysEx
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diff.impl.patch.FilePatch
import com.intellij.openapi.diff.impl.patch.PatchHunkUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.ex.isValidRanges
import com.intellij.openapi.vfs.VirtualFile
import git4idea.GitContentRevision
import org.jetbrains.annotations.ApiStatus

/**
 * Represents a set of changes in a branch compared to some other branch via three-dot-diff (via merge base)
 */
@ApiStatus.Experimental
interface GitBranchComparisonResult {
  /**
   * SHA hash of the tip of the "left" side of the comparison
   *
   * Typically, the tip of the branch where a review is merged
   */
  val baseSha: String

  /**
   * The most recent common ancestor of the "left" and "right" sides of the comparison
   * https://git-scm.com/docs/git-merge-base
   */
  val mergeBaseSha: String

  /**
   * SHA hash of the tip of the "right" side of the comparison
   *
   * Typically, the tip of the reviewed branch
   */
  val headSha: String

  /**
   * The list of "cumulative" changes between [baseSha] and [headSha] via [mergeBaseSha]
   * In essense - changes between [mergeBaseSha] and [headSha]
   */
  val changes: List<RefComparisonChange>

  /**
   * Ordered list of commits in the "right" branch where the last commit is the commit with [headSha]
   */
  val commits: List<GitCommitShaWithPatches>

  /**
   * Changes between each commit in [commits] and it's first parent
   */
  val changesByCommits: Map<String, List<RefComparisonChange>>

  /**
   * A container for parsed changes for both [changes] and [changesByCommits]
   *
   * A parsed change is represented by the [GitTextFilePatchWithHistory] which holds the parsed diff data of the file change and
   * a reference to the whole history of file changes in the "right" (code review) branch
   */
  val patchesByChange: Map<RefComparisonChange, GitTextFilePatchWithHistory>

  companion object {
    /**
     * Create a three-dot-diff comparison result holder
     *
     * @param vcsRoot root of the git repository
     * @param baseSha head revision of the "left" branch
     * @param mergeBaseSha merge base revision
     * @param commits commits from the "right" branch (not present in the "left")
     * where the last commit should be the head of the "right" branch
     * @param headPatches patches between merge base and last commit of the "right" branch
     */
    fun create(project: Project,
               vcsRoot: VirtualFile,
               baseSha: String,
               mergeBaseSha: String,
               commits: List<GitCommitShaWithPatches>,
               headPatches: List<FilePatch>): GitBranchComparisonResult =
      GitBranchComparisonResultImpl(project, vcsRoot, baseSha, mergeBaseSha, commits, headPatches)
  }
}

@ApiStatus.Experimental
fun GitBranchComparisonResult.findCumulativeChange(commitSha: String, filePath: String): RefComparisonChange? {
  for (change in changes) {
    if (patchesByChange[change]?.contains(commitSha, filePath) == true) {
      return change
    }
  }
  return null
}

@ApiStatus.Experimental
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

@ApiStatus.Experimental
fun RefComparisonChange.createVcsChange(project: Project): Change {
  val beforeRevision = filePathBefore?.let { GitContentRevision.createRevision(it, revisionNumberBefore, project) }
  val afterRevision = filePathAfter?.let { GitContentRevision.createRevision(it, revisionNumberAfter, project) }
  return Change(beforeRevision, afterRevision)
}