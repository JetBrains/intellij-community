// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.data.service

import org.jetbrains.plugins.github.api.data.GHRepository
import org.jetbrains.plugins.github.api.data.GHRepositoryPermissionLevel
import org.jetbrains.plugins.github.api.data.GHUser
import org.jetbrains.plugins.github.api.data.GithubUser
import org.jetbrains.plugins.github.api.data.pullrequest.GHTeam
import org.jetbrains.plugins.github.authentication.accounts.GithubAccount
import org.jetbrains.plugins.github.util.GithubSharedProjectSettings

class GHPRSecurityServiceImpl(private val sharedProjectSettings: GithubSharedProjectSettings,
                              override val ghostUser: GHUser,
                              override val account: GithubAccount,
                              override val currentUser: GHUser,
                              private val currentUserTeams: List<GHTeam>,
                              private val repo: GHRepository) : GHPRSecurityService {
  override fun isCurrentUser(user: GithubUser) = user.nodeId == currentUser.id

  override fun currentUserHasPermissionLevel(level: GHRepositoryPermissionLevel) =
    (repo.viewerPermission?.ordinal ?: -1) >= level.ordinal

  override fun isUserInAnyTeam(slugs: List<String>) = currentUserTeams.any { slugs.contains(it.slug) }

  override fun isMergeAllowed() = repo.mergeCommitAllowed
  override fun isRebaseMergeAllowed() = repo.rebaseMergeAllowed
  override fun isSquashMergeAllowed() = repo.squashMergeAllowed

  override fun isMergeForbiddenForProject() = sharedProjectSettings.pullRequestMergeForbidden
}