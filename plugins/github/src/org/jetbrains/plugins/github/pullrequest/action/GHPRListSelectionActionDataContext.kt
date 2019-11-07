// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.action

import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestShort
import org.jetbrains.plugins.github.pullrequest.avatars.CachingGithubAvatarIconsProvider
import org.jetbrains.plugins.github.pullrequest.data.GHPullRequestsDataContext
import org.jetbrains.plugins.github.pullrequest.data.GithubPullRequestDataProvider
import org.jetbrains.plugins.github.pullrequest.ui.GithubPullRequestsListSelectionHolder

class GHPRListSelectionActionDataContext internal constructor(private val dataContext: GHPullRequestsDataContext,
                                                              private val selectionHolder: GithubPullRequestsListSelectionHolder,
                                                              override val avatarIconsProviderFactory: CachingGithubAvatarIconsProvider.Factory)
  : GHPRActionDataContext {

  override val gitRepositoryCoordinates = dataContext.gitRepositoryCoordinates
  override val repositoryCoordinates = dataContext.repositoryCoordinates

  override val securityService = dataContext.securityService
  override val busyStateTracker = dataContext.busyStateTracker
  override val stateService = dataContext.stateService
  override val reviewService = dataContext.reviewService
  override val commentService = dataContext.commentService

  override val requestExecutor = dataContext.requestExecutor

  override val currentUser = dataContext.securityService.currentUser

  override fun resetAllData() {
    dataContext.metadataService.resetData()
    dataContext.listLoader.reset()
    dataContext.dataLoader.invalidateAllData()
  }

  override val pullRequest: Long?
    get() = selectionHolder.selectionNumber

  override val pullRequestDetails: GHPullRequestShort?
    get() = pullRequest?.let { dataContext.listLoader.findData(it) }

  override val pullRequestDataProvider: GithubPullRequestDataProvider?
    get() = pullRequest?.let { dataContext.dataLoader.getDataProvider(it) }
}