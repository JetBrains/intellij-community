// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.changes

import com.intellij.openapi.diff.impl.patch.FilePatch
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.changes.RefComparisonChange
import com.intellij.openapi.vcs.history.ShortVcsRevisionNumber
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
     * @param baseSha head revision of the "left" branch
     * @param mergeBaseSha merge base revision
     * @param commits commits from the "right" branch (not present in the "left")
     * where the last commit should be the head of the "right" branch
     * @param headPatches patches between merge base and last commit of the "right" branch
     * @param createRevisionNumber creates the revision of a change from a commit SHA
     * @param createFilePath creates the path of a change from a path relative to the repository root
     */
    fun create(
      baseSha: String,
      mergeBaseSha: String,
      commits: List<GitCommitShaWithPatches>,
      headPatches: List<FilePatch>,
      createRevisionNumber: (sha: String) -> ShortVcsRevisionNumber,
      createFilePath: (relativePath: String) -> FilePath,
    ): GitBranchComparisonResult =
      GitBranchComparisonResultImpl(baseSha, mergeBaseSha, commits, headPatches, createRevisionNumber, createFilePath)
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
