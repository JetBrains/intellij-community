// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.data.service

import com.intellij.collaboration.api.page.ApiPageUtil
import com.intellij.collaboration.async.BatchesLoader
import com.intellij.collaboration.async.nestedDisposable
import com.intellij.platform.util.coroutines.childScope
import git4idea.GitRemoteBranch
import git4idea.remote.GitRemoteUrlCoordinates
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.map
import org.jetbrains.plugins.github.api.GHGQLRequests
import org.jetbrains.plugins.github.api.GHRepositoryCoordinates
import org.jetbrains.plugins.github.api.GithubApiRequestExecutor
import org.jetbrains.plugins.github.api.GithubApiRequests
import org.jetbrains.plugins.github.api.data.GHLabel
import org.jetbrains.plugins.github.api.data.GHRepositoryOwnerName
import org.jetbrains.plugins.github.api.data.GHRepositoryPullRequestTemplate
import org.jetbrains.plugins.github.api.data.GHUser
import org.jetbrains.plugins.github.api.data.GithubUser
import org.jetbrains.plugins.github.api.data.GithubUserWithPermissions
import org.jetbrains.plugins.github.api.data.pullrequest.GHTeam
import org.jetbrains.plugins.github.api.executeSuspend
import org.jetbrains.plugins.github.api.util.GithubApiPagesLoader.batchesFlow

class GHPRRepositoryDataServiceImpl internal constructor(
  parentCs: CoroutineScope,
  private val requestExecutor: GithubApiRequestExecutor,
  override val remoteCoordinates: GitRemoteUrlCoordinates,
  override val repositoryCoordinates: GHRepositoryCoordinates,
  private val repoOwner: GHRepositoryOwnerName,
  override val repositoryId: String,
  override val defaultBranchName: String?,
  override val isFork: Boolean,
) : GHPRRepositoryDataService {
  private val cs = parentCs.childScope(javaClass.name)

  private val serverPath = repositoryCoordinates.serverPath
  private val repoPath = repositoryCoordinates.repositoryPath

  private val _dataReloadSignal = MutableSharedFlow<Unit>(replay = 1)
  override val dataReloadSignal: SharedFlow<Unit> = _dataReloadSignal.asSharedFlow()

  init {
    requestExecutor.addListener(cs.nestedDisposable()) {
      resetData()
    }
  }

  private val collaboratorsLoader by lazy {
    BatchesLoader(cs, batchesFlow(requestExecutor, GithubApiRequests.Repos.Collaborators.pages(serverPath,
                                                                                               repoPath.owner,
                                                                                               repoPath.repository)))
  }

  override fun loadBatchedCollaborators(): Flow<List<GithubUserWithPermissions>> = collaboratorsLoader.getBatches()

  private val contributorsLoader by lazy {
    val batchesFlow = batchesFlow(requestExecutor, GithubApiRequests.Repos.Contributors.pages(serverPath,
                                                                                              repoPath.owner,
                                                                                              repoPath.repository))
    BatchesLoader(cs, batchesFlow.mapBatchItems { it.toGHUser() })
  }

  override fun loadBatchedContributors(): Flow<List<GHUser>> = contributorsLoader.getBatches()

  private val assigneesLoader by lazy {
    val batchesFlow = batchesFlow(requestExecutor, GithubApiRequests.Repos.Assignees.pages(serverPath,
                                                                                           repoPath.owner,
                                                                                           repoPath.repository))
    BatchesLoader(cs, batchesFlow.mapBatchItems { it.toGHUser() })
  }

  override fun loadBatchedPotentialIssuesAssignees(): Flow<List<GHUser>> = assigneesLoader.getBatches()

  private fun GithubUser.toGHUser(): GHUser =
    GHUser(nodeId, login, htmlUrl, avatarUrl ?: "", null)

  private val labelsLoader by lazy {
    val batchesFlow = batchesFlow(requestExecutor, GithubApiRequests.Repos.Labels.pages(serverPath,
                                                                                        repoPath.owner,
                                                                                        repoPath.repository)
    )
    BatchesLoader(cs, batchesFlow.mapBatchItems { GHLabel(it.nodeId, it.url, it.name, it.color) })
  }

  override fun loadBatchedLabels(): Flow<List<GHLabel>> = labelsLoader.getBatches()

  private val teamsLoader by lazy {
    val pagesFlow = ApiPageUtil.createGQLPagesFlow {
      requestExecutor.executeSuspend(GHGQLRequests.Organization.Team.findAll(serverPath,
                                                                             repoOwner.login,
                                                                             it))
    }
    BatchesLoader(cs, pagesFlow.map { page -> page.nodes })
  }

  override fun mentionableUsersBatchesFlow(): Flow<List<GHUser>> = ApiPageUtil.createGQLPagesFlow {
    requestExecutor.executeSuspend(GHGQLRequests.Repo.findMentionableUsers(repositoryCoordinates, serverPath, it))
  }.map { it.nodes }


  override fun loadBatchedTeams(): Flow<List<GHTeam>> = teamsLoader.getBatches()

  private val templatesRequest: Deferred<List<GHRepositoryPullRequestTemplate>> = cs.async(start = CoroutineStart.LAZY) {
    requestExecutor.executeSuspend(GHGQLRequests.Repo.loadPullRequestTemplates(repositoryCoordinates)).orEmpty()
  }

  override suspend fun loadTemplate(): String? {
    return templatesRequest.await().find { !it.body.isNullOrBlank() }?.body
  }

  override fun resetData() {
    collaboratorsLoader.cancel()
    contributorsLoader.cancel()
    assigneesLoader.cancel()
    labelsLoader.cancel()
    teamsLoader.cancel()
    _dataReloadSignal.tryEmit(Unit)
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

  private fun <T, R> Flow<List<T>>.mapBatchItems(mapper: (T) -> R): Flow<List<R>> = map { batch -> batch.map(mapper) }
}