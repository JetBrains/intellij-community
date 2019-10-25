// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.ui.changes

import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.Change
import org.jetbrains.plugins.github.api.data.GHUser
import org.jetbrains.plugins.github.pullrequest.avatars.CachingGithubAvatarIconsProvider
import org.jetbrains.plugins.github.pullrequest.comment.GHPRDiffReviewSupport
import org.jetbrains.plugins.github.pullrequest.comment.GHPRDiffReviewSupportImpl
import org.jetbrains.plugins.github.pullrequest.data.GHPRChangesProvider
import org.jetbrains.plugins.github.pullrequest.data.GHPRReviewServiceAdapter
import org.jetbrains.plugins.github.pullrequest.data.GithubPullRequestDataProvider
import org.jetbrains.plugins.github.pullrequest.data.service.GHPRReviewService

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
}