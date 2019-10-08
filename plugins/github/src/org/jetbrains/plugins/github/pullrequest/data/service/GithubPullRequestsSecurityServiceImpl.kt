// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.data.service

import org.jetbrains.plugins.github.api.data.GHRepositoryPermission
import org.jetbrains.plugins.github.api.data.GHRepositoryPermissionLevel
import org.jetbrains.plugins.github.api.data.GHUser
import org.jetbrains.plugins.github.api.data.GithubUser
import org.jetbrains.plugins.github.util.GithubSharedProjectSettings

class GithubPullRequestsSecurityServiceImpl(private val sharedProjectSettings: GithubSharedProjectSettings,
                                            override val currentUser: GHUser,
                                            private val repo: GHRepositoryPermission) : GithubPullRequestsSecurityService {
  override fun isCurrentUser(user: GithubUser) = user.nodeId == currentUser.id
  override fun currentUserCanEditPullRequestsMetadata() = currentUserHasPermissionLevel(GHRepositoryPermissionLevel.TRIAGE)

  override fun currentUserHasPermissionLevel(level: GHRepositoryPermissionLevel) =
    (repo.viewerPermission?.ordinal ?: -1) >= level.ordinal

  override fun isMergeAllowed() = repo.mergeCommitAllowed
  override fun isRebaseMergeAllowed() = repo.rebaseMergeAllowed
  override fun isSquashMergeAllowed() = repo.squashMergeAllowed

  override fun isMergeForbiddenForProject() = sharedProjectSettings.pullRequestMergeForbidden
}