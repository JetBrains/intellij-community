// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.data.service

import git4idea.GitRemoteBranch
import git4idea.remote.GitRemoteUrlCoordinates
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.github.api.GHRepositoryCoordinates
import org.jetbrains.plugins.github.api.data.GHLabel
import org.jetbrains.plugins.github.api.data.GHUser
import org.jetbrains.plugins.github.api.data.GithubUserWithPermissions
import org.jetbrains.plugins.github.api.data.pullrequest.GHTeam
import org.jetbrains.plugins.github.util.GHGitRepositoryMapping

@ApiStatus.Internal
interface GHPRRepositoryDataService {
  val remoteCoordinates: GitRemoteUrlCoordinates
  val repositoryCoordinates: GHRepositoryCoordinates
  val repositoryMapping: GHGitRepositoryMapping
    get() = GHGitRepositoryMapping(repositoryCoordinates, remoteCoordinates)

  val repositoryId: String
  val defaultBranchName: String?
  val isFork: Boolean
  val dataReloadSignal: SharedFlow<Unit>

  fun loadBatchedCollaborators(): Flow<List<GithubUserWithPermissions>>

  fun loadBatchedContributors(): Flow<List<GHUser>>

  fun loadBatchedPotentialIssuesAssignees(): Flow<List<GHUser>>

  fun loadBatchedLabels(): Flow<List<GHLabel>>

  fun loadBatchedTeams(): Flow<List<GHTeam>>

  /**
   * Find a pull request description template
   */
  suspend fun loadTemplate(): String?

  fun resetData()

  fun getDefaultRemoteBranch(): GitRemoteBranch?

  fun mentionableUsersBatchesFlow(): Flow<List<GHUser>>
}