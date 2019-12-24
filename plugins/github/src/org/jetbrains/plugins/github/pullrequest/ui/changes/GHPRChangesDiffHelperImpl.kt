// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.ui.changes

import com.intellij.diff.comparison.ComparisonManagerImpl
import com.intellij.diff.comparison.ComparisonUtil
import com.intellij.diff.comparison.iterables.DiffIterableUtil
import com.intellij.diff.tools.util.text.LineOffsetsUtil
import com.intellij.diff.util.DiffUserDataKeysEx
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.Change
import org.jetbrains.plugins.github.api.data.GHUser
import org.jetbrains.plugins.github.pullrequest.avatars.CachingGithubAvatarIconsProvider
import org.jetbrains.plugins.github.pullrequest.comment.GHPRDiffReviewSupport
import org.jetbrains.plugins.github.pullrequest.comment.GHPRDiffReviewSupportImpl
import org.jetbrains.plugins.github.pullrequest.data.GHPRChangesProvider
import org.jetbrains.plugins.github.pullrequest.data.GithubPullRequestDataProvider
import org.jetbrains.plugins.github.pullrequest.data.service.GHPRReviewService
import org.jetbrains.plugins.github.pullrequest.data.service.GHPRReviewServiceAdapter

class GHPRChangesDiffHelperImpl(private val project: Project,
                                private val reviewService: GHPRReviewService,
                                private val avatarIconsProviderFactory: CachingGithubAvatarIconsProvider.Factory,
                                private val currentUser: GHUser)
  : GHPRChangesDiffHelper {
  private var dataProvider: GithubPullRequestDataProvider? = null
  private var changesProvider: GHPRChangesProvider? = null

  override fun setUp(dataProvider: GithubPullRequestDataProvider, changesProvider: GHPRChangesProvider) {
    this.dataProvider = dataProvider
    this.changesProvider = changesProvider
  }

  override fun reset() {
    dataProvider = null
    changesProvider = null
  }

  override fun getReviewSupport(change: Change): GHPRDiffReviewSupport? {
    val reviewService = dataProvider?.let { GHPRReviewServiceAdapter.create(reviewService, it) } ?: return null
    val diffRanges = changesProvider?.findDiffRanges(change) ?: return null
    val fileLinesMapper = changesProvider?.findFileLinesMapper(change) ?: return null
    val lastCommitSha = changesProvider?.lastCommitSha ?: return null
    val filePath = changesProvider?.getFilePath(change) ?: return null

    return GHPRDiffReviewSupportImpl(project, reviewService, diffRanges, fileLinesMapper, lastCommitSha, filePath,
                                     { commitSha, path -> changesProvider?.findChange(commitSha, path) == change },
                                     avatarIconsProviderFactory, currentUser)
  }

  override fun getDiffComputer(change: Change): DiffUserDataKeysEx.DiffComputer? {
    val diffRanges = changesProvider?.findDiffRangesWithoutContext(change) ?: return null

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