// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.action

import org.jetbrains.plugins.github.api.GHRepositoryCoordinates
import org.jetbrains.plugins.github.api.GithubApiRequestExecutor
import org.jetbrains.plugins.github.api.data.GHUser
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestShort
import org.jetbrains.plugins.github.pullrequest.avatars.CachingGithubAvatarIconsProvider
import org.jetbrains.plugins.github.pullrequest.data.GithubPullRequestDataProvider
import org.jetbrains.plugins.github.pullrequest.data.GithubPullRequestsBusyStateTracker
import org.jetbrains.plugins.github.pullrequest.data.service.GHPRCommentService
import org.jetbrains.plugins.github.pullrequest.data.service.GHPRReviewService
import org.jetbrains.plugins.github.pullrequest.data.service.GithubPullRequestsSecurityService
import org.jetbrains.plugins.github.pullrequest.data.service.GithubPullRequestsStateService
import org.jetbrains.plugins.github.util.GitRemoteUrlCoordinates

interface GHPRActionDataContext {

  val securityService: GithubPullRequestsSecurityService
  val busyStateTracker: GithubPullRequestsBusyStateTracker
  val stateService: GithubPullRequestsStateService
  val reviewService: GHPRReviewService
  val commentService: GHPRCommentService

  val requestExecutor: GithubApiRequestExecutor

  val gitRepositoryCoordinates: GitRemoteUrlCoordinates
  val repositoryCoordinates: GHRepositoryCoordinates

  val avatarIconsProviderFactory: CachingGithubAvatarIconsProvider.Factory
  val currentUser: GHUser

  val pullRequest: Long?
  val pullRequestDetails: GHPullRequestShort?
  val pullRequestDataProvider: GithubPullRequestDataProvider?

  fun resetAllData()
}