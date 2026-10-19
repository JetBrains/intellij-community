// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.changes

import com.intellij.diff.comparison.ComparisonManagerImpl.getInstanceImpl
import com.intellij.diff.comparison.iterables.DiffIterableUtil
import com.intellij.diff.tools.util.text.LineOffsetsUtil
import com.intellij.diff.util.DiffUserDataKeysEx
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diff.impl.patch.FilePatch
import com.intellij.openapi.diff.impl.patch.PatchHunkUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.RefComparisonChange
import com.intellij.openapi.vcs.ex.isValidRanges
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.vcsUtil.VcsUtil
import git4idea.GitContentRevision
import git4idea.GitRevisionNumber
import org.jetbrains.annotations.ApiStatus

/**
 * Create a three-dot-diff comparison result holder for the repository at [vcsRoot].
 *
 * The changes use [GitRevisionNumber], so [createVcsChange] can load their content.
 *
 * @param project not used. It stays, so that the callers of the earlier factory need no change.
 * @param vcsRoot root of the git repository
 * @param baseSha head revision of the "left" branch
 * @param mergeBaseSha merge base revision
 * @param commits commits from the "right" branch (not present in the "left")
 * where the last commit should be the head of the "right" branch
 * @param headPatches patches between merge base and last commit of the "right" branch
 */
@ApiStatus.Experimental
fun GitBranchComparisonResult.Companion.create(
  @Suppress("unused") project: Project,
  vcsRoot: VirtualFile,
  baseSha: String,
  mergeBaseSha: String,
  commits: List<GitCommitShaWithPatches>,
  headPatches: List<FilePatch>,
): GitBranchComparisonResult =
  create(baseSha, mergeBaseSha, commits, headPatches, ::GitRevisionNumber) { VcsUtil.getFilePath(vcsRoot, it) }

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