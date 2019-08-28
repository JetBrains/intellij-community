// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.action

import org.jetbrains.plugins.github.api.GHRepositoryCoordinates
import org.jetbrains.plugins.github.api.GithubApiRequestExecutor
import org.jetbrains.plugins.github.pullrequest.avatars.CachingGithubAvatarIconsProvider
import org.jetbrains.plugins.github.pullrequest.data.GHPRListLoader
import org.jetbrains.plugins.github.pullrequest.data.GithubPullRequestDataProvider
import org.jetbrains.plugins.github.pullrequest.data.GithubPullRequestsDataLoader
import org.jetbrains.plugins.github.pullrequest.data.service.GithubPullRequestsMetadataService
import org.jetbrains.plugins.github.pullrequest.ui.GithubPullRequestsListSelectionHolder
import org.jetbrains.plugins.github.util.GitRemoteUrlCoordinates

class GHPRActionDataContext internal constructor(val gitRepositoryCoordinates: GitRemoteUrlCoordinates,
                                                 val repositoryCoordinates: GHRepositoryCoordinates,
                                                 val requestExecutor: GithubApiRequestExecutor,
                                                 private val listLoader: GHPRListLoader,
                                                 private val dataLoader: GithubPullRequestsDataLoader,
                                                 private val metadataService: GithubPullRequestsMetadataService,
                                                 private val selectionHolder: GithubPullRequestsListSelectionHolder,
                                                 val avatarIconsProviderFactory: CachingGithubAvatarIconsProvider.Factory) {
  fun resetAllData() {
    metadataService.resetData()
    listLoader.reset()
    dataLoader.invalidateAllData()
  }

  private val selectedPullRequest: Long?
    get() = selectionHolder.selectionNumber

  val selectedPullRequestDataProvider: GithubPullRequestDataProvider?
    get() = selectedPullRequest?.let { dataLoader.getDataProvider(it) }

}