// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.data

import com.intellij.collaboration.api.page.ApiPageUtil
import com.intellij.collaboration.async.modelFlow
import com.intellij.collaboration.async.withInitial
import com.intellij.collaboration.util.ResultUtil.runCatchingUser
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.platform.util.coroutines.childScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.jetbrains.plugins.gitlab.api.*
import org.jetbrains.plugins.gitlab.api.data.GitLabPlan
import org.jetbrains.plugins.gitlab.api.dto.GitLabLabelDTO
import org.jetbrains.plugins.gitlab.api.dto.GitLabUserDTO
import org.jetbrains.plugins.gitlab.api.dto.GitLabWorkItemDTO.GitLabWidgetDTO.WorkItemWidgetAssignees
import org.jetbrains.plugins.gitlab.api.dto.GitLabWorkItemDTO.WorkItemType
import org.jetbrains.plugins.gitlab.api.request.*
import org.jetbrains.plugins.gitlab.mergerequest.api.dto.GitLabMergeRequestDTO
import org.jetbrains.plugins.gitlab.mergerequest.api.request.loadMergeRequest
import org.jetbrains.plugins.gitlab.mergerequest.api.request.mergeRequestSetReviewers
import org.jetbrains.plugins.gitlab.util.GitLabProjectMapping
import org.jetbrains.plugins.gitlab.util.GitLabRegistry

private val LOG = logger<GitLabProject>()

interface GitLabProject {
  val projectMapping: GitLabProjectMapping

  val mergeRequests: GitLabProjectMergeRequestsStore

  val labels: SharedFlow<Result<List<GitLabLabelDTO>>>
  val members: SharedFlow<Result<List<GitLabUserDTO>>>
  val defaultBranch: Deferred<String>
  val allowsMultipleReviewers: SharedFlow<Boolean>

  /**
   * Creates a merge request on the GitLab server and returns a DTO containing the merge request
   * once the merge request was successfully initialized on server.
   * The reason for this wait is that GitLab might take a few moments to process the merge request
   * before returning one that can be displayed in the IDE in a useful way.
   */
  suspend fun createMergeRequestAndAwaitCompletion(sourceBranch: String, targetBranch: String, title: String): GitLabMergeRequestDTO
  suspend fun adjustReviewers(mrIid: String, reviewers: List<GitLabUserDTO>): GitLabMergeRequestDTO

  fun reloadData()
}

@OptIn(ExperimentalCoroutinesApi::class)
class GitLabLazyProject(
  private val project: Project,
  parentCs: CoroutineScope,
  private val api: GitLabApi,
  private val glMetadata: GitLabServerMetadata?,
  override val projectMapping: GitLabProjectMapping,
  private val tokenRefreshFlow: Flow<Unit>
) : GitLabProject {

  private val cs = parentCs.childScope()

  private val projectCoordinates: GitLabProjectCoordinates = projectMapping.repository

  private val projectDataReloadSignal = MutableSharedFlow<Unit>()

  override val mergeRequests by lazy {
    CachingGitLabProjectMergeRequestsStore(project, cs, api, glMetadata, projectMapping, tokenRefreshFlow)
  }

  override val labels: SharedFlow<Result<List<GitLabLabelDTO>>> = resultListFlow {
    api.graphQL.createAllProjectLabelsFlow(projectMapping.repository)
  }.triggerOn(projectDataReloadSignal.withInitial(Unit))
    .modelFlow(parentCs, LOG)

  override val members: SharedFlow<Result<List<GitLabUserDTO>>> = resultListFlow {
    ApiPageUtil.createPagesFlowByLinkHeader(getProjectUsersURI(projectMapping.repository)) { api.rest.getProjectUsers(it) }
      .map { response -> response.body().map(GitLabUserDTO::fromRestDTO) }
  }.triggerOn(projectDataReloadSignal.withInitial(Unit))
    .modelFlow(parentCs, LOG)

  override val defaultBranch: Deferred<String> = cs.async(Dispatchers.IO, start = CoroutineStart.LAZY) {
    val projectRepository = api.graphQL.getProjectRepository(projectCoordinates).body()
    projectRepository.rootRef
  }

  private val plan: Deferred<GitLabPlan?> = cs.async(Dispatchers.IO, start = CoroutineStart.LAZY) {
    runCatchingUser {
      val namespace = api.rest.getProjectNamespace(projectMapping.repository.projectPath.owner).body()
      namespace?.plan
    }.getOrNull()
  }

  // TODO: Change the implementation after adding `allowsMultipleReviewers` field to the API
  //  https://gitlab.com/gitlab-org/gitlab/-/issues/431829
  override val allowsMultipleReviewers: SharedFlow<Boolean> = channelFlow {
    val glPlan = plan.await()
    if (glPlan != null) {
      send(glPlan != GitLabPlan.FREE)
      return@channelFlow
    }

    if (glMetadata != null && glMetadata.version >= GitLabVersion(15, 2)) {
      send(getAllowsMultipleAssigneesPropertyFromIssueWidget())
      return@channelFlow
    }

    send(false)
  }.modelFlow(parentCs, LOG)

  @Throws(GitLabGraphQLMutationException::class)
  override suspend fun createMergeRequestAndAwaitCompletion(sourceBranch: String, targetBranch: String, title: String): GitLabMergeRequestDTO {
    return withContext(cs.coroutineContext + Dispatchers.IO) {
      var data: GitLabMergeRequestDTO = api.graphQL.createMergeRequest(projectCoordinates, sourceBranch, targetBranch, title).getResultOrThrow()
      val iid = data.iid
      var attempts = 1
      while (attempts++ < GitLabRegistry.getRequestPollingAttempts()) {
        val updatedMr = api.graphQL.loadMergeRequest(projectCoordinates, iid).body()
        requireNotNull(updatedMr)

        data = updatedMr

        if (data.diffRefs != null) break

        delay(GitLabRegistry.getRequestPollingIntervalMillis().toLong())
      }
      data
    }
  }

  @Throws(GitLabGraphQLMutationException::class, IllegalStateException::class)
  override suspend fun adjustReviewers(mrIid: String, reviewers: List<GitLabUserDTO>): GitLabMergeRequestDTO {
    return withContext(cs.coroutineContext + Dispatchers.IO) {
      if (GitLabVersion(15, 3) <= api.getMetadata().version) {
        api.graphQL.mergeRequestSetReviewers(projectCoordinates, mrIid, reviewers).getResultOrThrow()
      }
      else {
        api.rest.mergeRequestSetReviewers(projectCoordinates, mrIid, reviewers).body()
        api.graphQL.loadMergeRequest(projectCoordinates, mrIid).body() ?: error("Merge request could not be loaded")
      }
    }
  }

  override fun reloadData() {
    cs.launch {
      projectDataReloadSignal.emit(Unit)
    }
  }

  private suspend fun getAllowsMultipleAssigneesPropertyFromIssueWidget(): Boolean {
    val widgetAssignees: WorkItemWidgetAssignees? = resultListFlow {
      api.graphQL.createAllWorkItemsFlow(projectMapping.repository)
    }.transformWhile { resultedWorkItems ->
      val items = resultedWorkItems.getOrNull() ?: return@transformWhile false
      val widget = items.find { workItem -> workItem.workItemType.name == WorkItemType.ISSUE_TYPE }
        ?.widgets
        ?.asSequence()
        ?.filterIsInstance<WorkItemWidgetAssignees>()
        ?.first()

      if (widget != null) {
        emit(widget)
        return@transformWhile false
      }
      else {
        return@transformWhile true
      }
    }.firstOrNull()

    return widgetAssignees?.allowsMultipleAssignees ?: false
  }

  private fun <T> resultListFlow(flowProvider: () -> Flow<List<T>>): Flow<Result<List<T>>> = channelFlow<Result<List<T>>> {
    runCatchingUser {
      val loadedItems = mutableListOf<T>()
      val itemsFlow = flowProvider()
      itemsFlow.collect { items ->
        loadedItems.addAll(items)
        send(Result.success(loadedItems))
      }
    }.onFailure { e -> send(Result.failure(e)) }
  }

  // NOTE: works only with cold flow
  private fun <T> Flow<T>.triggerOn(signalFlow: Flow<Unit>): Flow<T> {
    val originalFlow = this
    return signalFlow.flatMapLatest { originalFlow }
  }
}