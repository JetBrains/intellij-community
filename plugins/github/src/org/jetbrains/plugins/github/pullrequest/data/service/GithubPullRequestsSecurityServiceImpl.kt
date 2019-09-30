// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.data.service

import org.jetbrains.plugins.github.api.data.GHUser
import org.jetbrains.plugins.github.api.data.GithubRepoDetailed
import org.jetbrains.plugins.github.api.data.GithubUser
import org.jetbrains.plugins.github.util.GithubSharedProjectSettings

class GithubPullRequestsSecurityServiceImpl(private val sharedProjectSettings: GithubSharedProjectSettings,
                                            override val currentUser: GHUser,
                                            private val repo: GithubRepoDetailed) : GithubPullRequestsSecurityService {
  override fun isCurrentUser(user: GithubUser) = user.nodeId == currentUser.id
  override fun isCurrentUserWithPushAccess() = repo.permissions.isPush || repo.permissions.isAdmin

  override fun isMergeAllowed() = repo.allowMergeCommit
  override fun isRebaseMergeAllowed() = repo.allowRebaseMerge
  override fun isSquashMergeAllowed() = repo.allowSquashMerge

  override fun isMergeForbiddenForProject() = sharedProjectSettings.pullRequestMergeForbidden
}