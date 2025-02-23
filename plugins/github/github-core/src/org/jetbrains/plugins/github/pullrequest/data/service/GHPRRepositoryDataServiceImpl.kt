// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.data.service

import com.intellij.collaboration.api.page.ApiPageUtil
import com.intellij.collaboration.api.page.foldToList
import com.intellij.collaboration.async.awaitCompleted
import com.intellij.collaboration.async.mapScoped
import com.intellij.collaboration.async.nestedDisposable
import com.intellij.platform.util.coroutines.childScope
import git4idea.GitRemoteBranch
import git4idea.remote.GitRemoteUrlCoordinates
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.*
import org.jetbrains.plugins.github.api.*
import org.jetbrains.plugins.github.api.data.*
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestRequestedReviewer
import org.jetbrains.plugins.github.api.data.pullrequest.GHTeam
import org.jetbrains.plugins.github.api.util.GithubApiPagesLoader.batchesFlow

class GHPRRepositoryDataServiceImpl internal constructor(parentCs: CoroutineScope,
                                                         private val requestExecutor: GithubApiRequestExecutor,
                                                         override val remoteCoordinates: GitRemoteUrlCoordinates,
                                                         override val repositoryCoordinates: GHRepositoryCoordinates,
                                                         private val repoOwner: GHRepositoryOwnerName,
                                                         override val repositoryId: String,
                                                         override val defaultBranchName: String?,
                                                         override val isFork: Boolean)
  : GHPRRepositoryDataService {
  private val cs = parentCs.childScope(javaClass.name)

  private val serverPath = repositoryCoordinates.serverPath
  private val repoPath = repositoryCoordinates.repositoryPath

  init {
    requestExecutor.addListener(cs.nestedDisposable()) {
      resetData()
    }
  }

  private val collaboratorsRequest: MutableStateFlow<Deferred<List<GithubUserWithPermissions>>> by lazy {
    MutableStateFlow(doLoadCollaboratorsAsync())
  }

  private fun doLoadCollaboratorsAsync(): Deferred<List<GithubUserWithPermissions>> = cs.async {
    val pagesRequest = GithubApiRequests.Repos.Collaborators.pages(serverPath, repoPath.owner, repoPath.repository)
    batchesFlow(requestExecutor, pagesRequest).foldToList()
  }

  private val convertedCollaboratorsRequest: Flow<Deferred<List<GHUser>>> by lazy {
    collaboratorsRequest.mapScoped(true) {
      async {
        it.await().map { GHUser(it.nodeId, it.login, it.htmlUrl, it.avatarUrl ?: "", null) }
      }
    }.shareIn(cs, SharingStarted.Eagerly, 1)
  }

  override suspend fun loadCollaborators(): List<GHUser> = convertedCollaboratorsRequest.awaitCompleted()

  private val assigneesRequest: MutableStateFlow<Deferred<List<GHUser>>> by lazy {
    MutableStateFlow(doLoadIssuesAssigneesAsync())
  }

  private fun doLoadIssuesAssigneesAsync(): Deferred<List<GHUser>> = cs.async {
    batchesFlow(requestExecutor,
                GithubApiRequests.Repos.Assignees.pages(serverPath, repoPath.owner, repoPath.repository)).foldToList()
      .map { GHUser(it.nodeId, it.login, it.htmlUrl, it.avatarUrl ?: "", null) }
  }

  override suspend fun loadIssuesAssignees(): List<GHUser> = assigneesRequest.awaitCompleted()

  private val labelsRequest: MutableStateFlow<Deferred<List<GHLabel>>> by lazy {
    MutableStateFlow(doLoadLabelsAsync())
  }

  private fun doLoadLabelsAsync(): Deferred<List<GHLabel>> = cs.async {
    batchesFlow(requestExecutor,
                GithubApiRequests.Repos.Labels.pages(serverPath, repoPath.owner, repoPath.repository)).foldToList()
      .map { GHLabel(it.nodeId, it.url, it.name, it.color) }
  }

  override suspend fun loadLabels(): List<GHLabel> = labelsRequest.awaitCompleted()

  private val teamsRequest: MutableStateFlow<Deferred<List<GHTeam>>> by lazy {
    MutableStateFlow(doLoadTeamsAsync())
  }

  private fun doLoadTeamsAsync(): Deferred<List<GHTeam>> = cs.async {
    if (repoOwner !is GHRepositoryOwnerName.Organization) emptyList()
    else ApiPageUtil.createGQLPagesFlow {
      requestExecutor.executeSuspend(GHGQLRequests.Organization.Team.findAll(serverPath, repoOwner.login, it))
    }.fold(mutableListOf()) { acc, value ->
      acc.addAll(value.nodes)
      acc
    }
  }

  private val potentialReviewersRequest: Flow<Deferred<List<GHPullRequestRequestedReviewer>>> by lazy {
    combine(teamsRequest, collaboratorsRequest) { teamsReq, collaboratorsReq ->
      // can't await here bc combine transformer is not cancelled on new emissions
      suspend {
        val teams = teamsReq.await()
        val collaboratorsWithWriteAccess = collaboratorsReq.await().filter { it.permissions.isPush }
          .map { GHUser(it.nodeId, it.login, it.htmlUrl, it.avatarUrl ?: "", null) }
        teams + collaboratorsWithWriteAccess
      }
    }.mapScoped(true) { awaiter ->
      async {
        awaiter()
      }
    }.shareIn(cs, SharingStarted.Eagerly, 1)
  }

  override suspend fun loadPotentialReviewers(): List<GHPullRequestRequestedReviewer> = potentialReviewersRequest.awaitCompleted()

  private val templatesRequest: Deferred<List<GHRepositoryPullRequestTemplate>> = cs.async(start = CoroutineStart.LAZY) {
    requestExecutor.executeSuspend(GHGQLRequests.Repo.loadPullRequestTemplates(repositoryCoordinates)).orEmpty()
  }

  override suspend fun loadTemplate(): String? {
    return templatesRequest.await().find { it.body != null && it.body.isNotBlank() }?.body
  }

  override fun resetData() {
    collaboratorsRequest.restart(doLoadCollaboratorsAsync())
    teamsRequest.restart(doLoadTeamsAsync())
    assigneesRequest.restart(doLoadIssuesAssigneesAsync())
    labelsRequest.restart(doLoadLabelsAsync())
  }

  override fun getDefaultRemoteBranch(): GitRemoteBranch? {
    val currentRemote = repositoryMapping.remote
    val currentRepo = currentRemote.repository
    val branches = currentRepo.branches
    if (defaultBranchName != null) {
      return branches.findRemoteBranch("${currentRemote.remote.name}/$defaultBranchName")
    }

    return branches.findRemoteBranch("${currentRemote.remote.name}/master")
           ?: branches.findRemoteBranch("${currentRemote.remote.name}/main")
  }
}

private fun <T> MutableStateFlow<Deferred<T>>.restart(newRequest: Deferred<T>) {
  update {
    it.cancel()
    newRequest
  }
}