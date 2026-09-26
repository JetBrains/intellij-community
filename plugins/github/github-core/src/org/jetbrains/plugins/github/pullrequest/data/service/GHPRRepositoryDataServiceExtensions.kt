// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.data.service

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import org.jetbrains.plugins.github.api.data.GHRepositoryPermissionLevel
import org.jetbrains.plugins.github.api.data.GHUser
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestRequestedReviewer

fun GHPRRepositoryDataService.getBatchedPotentialReviewers(securityService: GHPRSecurityService): Flow<List<GHPullRequestRequestedReviewer>> {
  return channelFlow {
    launch {
      loadBatchedTeams().collect { send(it) }
    }
    launch {
      if (securityService.currentUserHasPermissionLevel(GHRepositoryPermissionLevel.WRITE)) {
        loadBatchedCollaborators().collect { collaboratorsBatch ->
          send(
            collaboratorsBatch
              .filter { it.permissions.isPush }
              .map { user -> GHUser(user.nodeId, user.login, user.htmlUrl, user.avatarUrl ?: "", null) }
          )
        }
      }
      else loadBatchedContributors().collect { send(it) }
    }
  }
}