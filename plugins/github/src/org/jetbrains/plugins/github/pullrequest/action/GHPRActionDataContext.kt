// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.action

import org.jetbrains.plugins.github.api.GHRepositoryCoordinates
import org.jetbrains.plugins.github.api.GithubApiRequestExecutor
import org.jetbrains.plugins.github.pullrequest.avatars.CachingGithubAvatarIconsProvider
import org.jetbrains.plugins.github.pullrequest.data.GHPullRequestsDataContext
import org.jetbrains.plugins.github.pullrequest.data.GithubPullRequestDataProvider
import org.jetbrains.plugins.github.pullrequest.ui.GithubPullRequestsListSelectionHolder
import org.jetbrains.plugins.github.util.GitRemoteUrlCoordinates

class GHPRActionDataContext internal constructor(private val dataContext: GHPullRequestsDataContext,
                                                 private val selectionHolder: GithubPullRequestsListSelectionHolder,
                                                 val avatarIconsProviderFactory: CachingGithubAvatarIconsProvider.Factory) {

  val gitRepositoryCoordinates: GitRemoteUrlCoordinates = dataContext.gitRepositoryCoordinates
  val repositoryCoordinates: GHRepositoryCoordinates = dataContext.repositoryCoordinates
  val requestExecutor: GithubApiRequestExecutor = dataContext.requestExecutor

  fun resetAllData() {
    dataContext.metadataService.resetData()
    dataContext.listLoader.reset()
    dataContext.dataLoader.invalidateAllData()
  }

  private val selectedPullRequest: Long?
    get() = selectionHolder.selectionNumber

  val selectedPullRequestDataProvider: GithubPullRequestDataProvider?
    get() = selectedPullRequest?.let { dataContext.dataLoader.getDataProvider(it) }

}