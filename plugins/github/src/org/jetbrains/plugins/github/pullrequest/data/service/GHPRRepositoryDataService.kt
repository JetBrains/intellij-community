// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.data.service

import git4idea.GitRemoteBranch
import git4idea.remote.GitRemoteUrlCoordinates
import kotlinx.coroutines.Deferred
import org.jetbrains.annotations.ApiStatus.Obsolete
import org.jetbrains.plugins.github.api.GHRepositoryCoordinates
import org.jetbrains.plugins.github.api.data.GHLabel
import org.jetbrains.plugins.github.api.data.GHUser
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestRequestedReviewer
import org.jetbrains.plugins.github.util.GHGitRepositoryMapping

interface GHPRRepositoryDataService {
  val remoteCoordinates: GitRemoteUrlCoordinates
  val repositoryCoordinates: GHRepositoryCoordinates
  val repositoryMapping: GHGitRepositoryMapping
    get() = GHGitRepositoryMapping(repositoryCoordinates, remoteCoordinates)

  val repositoryId: String
  val defaultBranchName: String?
  val isFork: Boolean

  suspend fun loadCollaborators(): List<GHUser>

  suspend fun loadIssuesAssignees(): List<GHUser>
  @Obsolete
  fun loadIssuesAssigneesAsync(): Deferred<List<GHUser>>

  suspend fun loadLabels(): List<GHLabel>
  @Obsolete
  fun loadLabelsAsync(): Deferred<List<GHLabel>>

  suspend fun loadPotentialReviewers(): List<GHPullRequestRequestedReviewer>
  @Obsolete
  fun loadPotentialReviewersAsync(): Deferred<List<GHPullRequestRequestedReviewer>>

  fun resetData()

  fun getDefaultRemoteBranch(): GitRemoteBranch?
}