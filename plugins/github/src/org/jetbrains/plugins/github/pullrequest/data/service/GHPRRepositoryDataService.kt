// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.data.service

import com.intellij.openapi.Disposable
import com.intellij.util.concurrency.annotations.RequiresEdt
import org.jetbrains.plugins.github.api.GHRepositoryCoordinates
import org.jetbrains.plugins.github.api.data.GHLabel
import org.jetbrains.plugins.github.api.data.GHUser
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestRequestedReviewer
import org.jetbrains.plugins.github.api.data.pullrequest.GHTeam
import org.jetbrains.plugins.github.util.GHGitRepositoryMapping
import org.jetbrains.plugins.github.util.GitRemoteUrlCoordinates
import java.util.concurrent.CompletableFuture

interface GHPRRepositoryDataService : Disposable {
  val remoteCoordinates: GitRemoteUrlCoordinates
  val repositoryCoordinates: GHRepositoryCoordinates
  val repositoryMapping: GHGitRepositoryMapping
    get() = GHGitRepositoryMapping(repositoryCoordinates, remoteCoordinates)

  val repositoryId: String
  val defaultBranchName: String
  val isFork: Boolean

  val collaborators: CompletableFuture<List<GHUser>>
  val teams: CompletableFuture<List<GHTeam>>
  val potentialReviewers: CompletableFuture<List<GHPullRequestRequestedReviewer>>
  val issuesAssignees: CompletableFuture<List<GHUser>>
  val labels: CompletableFuture<List<GHLabel>>

  @RequiresEdt
  fun resetData()
}