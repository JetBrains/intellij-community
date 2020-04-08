// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.ui.changes

import com.intellij.diff.comparison.ComparisonManagerImpl
import com.intellij.diff.comparison.ComparisonUtil
import com.intellij.diff.comparison.iterables.DiffIterableUtil
import com.intellij.diff.tools.util.text.LineOffsetsUtil
import com.intellij.diff.util.DiffUserDataKeysEx
import com.intellij.openapi.diff.impl.patch.PatchReader
import com.intellij.openapi.vcs.changes.Change
import org.jetbrains.plugins.github.api.data.GHUser
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestReviewThread
import org.jetbrains.plugins.github.pullrequest.avatars.CachingGithubAvatarIconsProvider
import org.jetbrains.plugins.github.pullrequest.comment.GHPRDiffReviewSupport
import org.jetbrains.plugins.github.pullrequest.comment.GHPRDiffReviewSupportImpl
import org.jetbrains.plugins.github.pullrequest.comment.GHPRDiffReviewThreadMapping
import org.jetbrains.plugins.github.pullrequest.data.GHPRChangeDiffData
import org.jetbrains.plugins.github.pullrequest.data.GHPRChangesProvider
import org.jetbrains.plugins.github.pullrequest.data.GHPRDataProvider
import org.jetbrains.plugins.github.util.GHPatchHunkUtil

class GHPRChangesDiffHelperImpl(private val avatarIconsProviderFactory: CachingGithubAvatarIconsProvider.Factory,
                                private val currentUser: GHUser)
  : GHPRChangesDiffHelper {
  private var dataProvider: GHPRDataProvider? = null
  private var changesProvider: GHPRChangesProvider? = null

  override fun setUp(dataProvider: GHPRDataProvider, changesProvider: GHPRChangesProvider) {
    this.dataProvider = dataProvider
    this.changesProvider = changesProvider
  }

  override fun reset() {
    dataProvider = null
    changesProvider = null
  }

  override fun getReviewSupport(change: Change): GHPRDiffReviewSupport? {
    val reviewDataProvider = dataProvider?.reviewData ?: return null

    return changesProvider?.let { provider ->
      val diffData = provider.findChangeDiffData(change) ?: return null
      val createReviewCommentHelper = GHPRCreateDiffCommentParametersHelper(diffData.commitSha, diffData.filePath, diffData.linesMapper)

      return GHPRDiffReviewSupportImpl(reviewDataProvider, diffData.diffRanges,
                                       { mapThread(diffData, it) },
                                       createReviewCommentHelper,
                                       avatarIconsProviderFactory, currentUser)
    }
  }

  private fun mapThread(diffData: GHPRChangeDiffData, thread: GHPullRequestReviewThread): GHPRDiffReviewThreadMapping? {
    val originalCommitSha = thread.originalCommit?.oid ?: return null
    if (!diffData.contains(originalCommitSha, thread.path)) return null

    val (side, line) = when (diffData) {
      is GHPRChangeDiffData.Cumulative -> thread.position?.let { diffData.linesMapper.findFileLocation(it) } ?: return null
      is GHPRChangeDiffData.Commit -> {
        val patchReader = PatchReader(GHPatchHunkUtil.createPatchFromHunk(thread.path, thread.diffHunk))
        patchReader.readTextPatches()
        val patchHunk = patchReader.textPatches[0].hunks.lastOrNull() ?: return null
        val position = GHPatchHunkUtil.getHunkLinesCount(patchHunk) - 1
        val (unmappedSide, unmappedLine) = GHPatchHunkUtil.findSideFileLineFromHunkLineIndex(patchHunk, position) ?: return null
        diffData.mapPosition(originalCommitSha, unmappedSide, unmappedLine) ?: return null
      }
    }

    return GHPRDiffReviewThreadMapping(side, line, thread)
  }

  override fun getDiffComputer(change: Change): DiffUserDataKeysEx.DiffComputer? {
    val diffRanges = changesProvider?.findChangeDiffData(change)?.diffRangesWithoutContext ?: return null

    return DiffUserDataKeysEx.DiffComputer { text1, text2, policy, innerChanges, indicator ->
      val comparisonManager = ComparisonManagerImpl.getInstanceImpl()
      val lineOffsets1 = LineOffsetsUtil.create(text1)
      val lineOffsets2 = LineOffsetsUtil.create(text2)

      if (!ComparisonUtil.isValidRanges(text1, text2, lineOffsets1, lineOffsets2, diffRanges)) {
        error("Invalid diff line ranges for change $change")
      }
      val iterable = DiffIterableUtil.create(diffRanges, lineOffsets1.lineCount, lineOffsets2.lineCount)
      DiffIterableUtil.iterateAll(iterable).map {
        comparisonManager.compareLinesInner(it.first, text1, text2, lineOffsets1, lineOffsets2, policy, innerChanges,
                                            indicator)
      }.flatten()
    }
  }
}