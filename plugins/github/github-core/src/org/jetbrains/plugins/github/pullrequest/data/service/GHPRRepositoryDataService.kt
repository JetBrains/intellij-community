// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.data.service

import git4idea.GitRemoteBranch
import git4idea.remote.GitRemoteUrlCoordinates
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.github.api.GHRepositoryCoordinates
import org.jetbrains.plugins.github.api.data.GHLabel
import org.jetbrains.plugins.github.api.data.GHUser
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestRequestedReviewer
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

  suspend fun loadCollaborators(): List<GHUser>

  suspend fun loadIssuesAssignees(): List<GHUser>

  suspend fun loadLabels(): List<GHLabel>

  suspend fun loadPotentialReviewers(): List<GHPullRequestRequestedReviewer>

  /**
   * Find a pull request description template
   */
  suspend fun loadTemplate(): String?

  fun resetData()

  fun getDefaultRemoteBranch(): GitRemoteBranch?
}