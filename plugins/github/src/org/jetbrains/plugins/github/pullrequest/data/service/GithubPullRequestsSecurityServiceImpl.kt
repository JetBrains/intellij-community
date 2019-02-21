// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.data.service

import org.jetbrains.plugins.github.api.data.GithubAuthenticatedUser
import org.jetbrains.plugins.github.api.data.GithubRepoDetailed
import org.jetbrains.plugins.github.api.data.GithubUser
import org.jetbrains.plugins.github.util.GithubSharedProjectSettings

class GithubPullRequestsSecurityServiceImpl(private val sharedProjectSettings: GithubSharedProjectSettings,
                                            private val currentUser: GithubAuthenticatedUser,
                                            private val repo: GithubRepoDetailed) : GithubPullRequestsSecurityService {
  override fun isCurrentUser(user: GithubUser) = user == currentUser
  override fun isCurrentUserWithPushAccess() = repo.permissions.isPush || repo.permissions.isAdmin

  override fun isMergeAllowed() = repo.allowMergeCommit
  override fun isRebaseMergeAllowed() = repo.allowRebaseMerge
  override fun isSquashMergeAllowed() = repo.allowSquashMerge

  override fun isMergeForbiddenForProject() = sharedProjectSettings.pullRequestMergeForbidden
}