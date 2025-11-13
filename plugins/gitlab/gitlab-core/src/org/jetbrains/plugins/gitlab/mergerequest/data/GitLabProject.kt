// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.data

import com.intellij.collaboration.api.page.ApiPageUtil
import com.intellij.collaboration.util.CodeReviewDomainEntity
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.platform.util.coroutines.childScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.jetbrains.plugins.gitlab.api.*
import org.jetbrains.plugins.gitlab.api.data.GitLabPlan
import org.jetbrains.plugins.gitlab.api.dto.GitLabLabelDTO
import org.jetbrains.plugins.gitlab.api.dto.GitLabProjectDTO
import org.jetbrains.plugins.gitlab.api.dto.GitLabUserDTO
import org.jetbrains.plugins.gitlab.api.dto.GitLabWorkItemDTO.GitLabWidgetDTO.WorkItemWidgetAssignees
import org.jetbrains.plugins.gitlab.api.dto.GitLabWorkItemDTO.WorkItemType
import org.jetbrains.plugins.gitlab.api.request.*
import org.jetbrains.plugins.gitlab.mergerequest.api.dto.GitLabMergeRequestDTO
import org.jetbrains.plugins.gitlab.mergerequest.api.request.loadMergeRequest
import org.jetbrains.plugins.gitlab.mergerequest.api.request.mergeRequestSetReviewers
import org.jetbrains.plugins.gitlab.upload.markdownUploadFile
import org.jetbrains.plugins.gitlab.util.GitLabProjectMapping
import org.jetbrains.plugins.gitlab.util.GitLabRegistry
import org.jetbrains.plugins.gitlab.util.GitLabStatistics
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO


private val LOG = logger<GitLabProject>()

@CodeReviewDomainEntity
interface GitLabProject {
  val projectMapping: GitLabProjectMapping

  val dataReloadSignal: SharedFlow<Unit>
  val mergeRequests: GitLabProjectMergeRequestsStore

  suspend fun getEmojis(): List<GitLabReaction>

  fun getLabelsBatches(): Flow<List<GitLabLabelDTO>>
  fun getMembersBatches(): Flow<List<GitLabUserDTO>>

  val defaultBranch: String?
  val gitLabProjectId: GitLabId
  suspend fun isMultipleReviewersAllowed(): Boolean

  /**
   * Creates a merge request on the GitLab server and returns a DTO containing the merge request
   * once the merge request was successfully initialized on server.
   * The reason for this wait is that GitLab might take a few moments to process the merge request
   * before returning one that can be displayed in the IDE in a useful way.
   */
  suspend fun createMergeRequestAndAwaitCompletion(sourceBranch: String, targetBranch: String, title: String, description: String?): GitLabMergeRequestDTO
  suspend fun adjustReviewers(mrIid: String, reviewers: List<GitLabUserDTO>): GitLabMergeRequestDTO

  fun reloadData()

  suspend fun uploadFile(path: Path): String
  suspend fun uploadImage(image: BufferedImage): String
  fun canUploadFile(): Boolean
}

@CodeReviewDomainEntity
class GitLabLazyProject(
  private val project: Project,
  parentCs: CoroutineScope,
  private val api: GitLabApi,
  private val glMetadata: GitLabServerMetadata?,
  override val projectMapping: GitLabProjectMapping,
  private val initialData: GitLabProjectDTO,
  private val currentUser: GitLabUserDTO,
  private val tokenRefreshFlow: Flow<Unit>,
) : GitLabProject {

  private val cs = parentCs.childScope(javaClass.name)

  private val projectCoordinates: GitLabProjectCoordinates = projectMapping.repository

  private val _dataReloadSignal = MutableSharedFlow<Unit>(replay = 1)
  override val dataReloadSignal: SharedFlow<Unit> = _dataReloadSignal.asSharedFlow()

  private val emojisRequest = cs.async(start = CoroutineStart.LAZY) {
    serviceAsync<GitLabEmojiService>().emojis.await().map { GitLabReactionImpl(it) }  }

  private val multipleReviewersAllowedRequest = cs.async(Dispatchers.IO, start = CoroutineStart.LAZY) {
    loadMultipleReviewersAllowed(initialData)
  }
  override val gitLabProjectId: GitLabId = initialData.id

  override val mergeRequests by lazy {
    CachingGitLabProjectMergeRequestsStore(project, cs, api, glMetadata, projectMapping, currentUser, tokenRefreshFlow)
  }

  private val labelsLoader = BatchesLoader(cs, api.graphQL.createAllProjectLabelsFlow(projectMapping.repository))
  private val membersLoader = BatchesLoader(cs,
                                            ApiPageUtil.createPagesFlowByLinkHeader(getProjectUsersURI(projectMapping.repository)) {
                                              api.rest.getProjectUsers(it)
                                            }.map { response -> response.body().map(GitLabUserDTO::fromRestDTO) })

  override suspend fun getEmojis(): List<GitLabReaction> = emojisRequest.await()
  override val defaultBranch: String? = initialData.repository?.rootRef
  override suspend fun isMultipleReviewersAllowed(): Boolean = multipleReviewersAllowedRequest.await()
  override fun getLabelsBatches(): Flow<List<GitLabLabelDTO>> = labelsLoader.getBatches()
  override fun getMembersBatches(): Flow<List<GitLabUserDTO>> = membersLoader.getBatches()

  private suspend fun loadMultipleReviewersAllowed(project: GitLabProjectDTO): Boolean {
    if (project.allowsMultipleMergeRequestReviewers != null) {
      return project.allowsMultipleMergeRequestReviewers
    }

    val fromPlan = getAllowsMultipleAssigneesPropertyFromNamespacePlan()
    if (fromPlan != null) {
      return fromPlan
    }

    if (glMetadata != null && glMetadata.version >= GitLabVersion(15, 2)) {
      return getAllowsMultipleAssigneesPropertyFromIssueWidget()
    }

    return false
  }

  @Throws(GitLabGraphQLMutationException::class)
  override suspend fun createMergeRequestAndAwaitCompletion(sourceBranch: String, targetBranch: String, title: String, description: String?): GitLabMergeRequestDTO {
    return withContext(cs.coroutineContext + Dispatchers.IO) {
      var data: GitLabMergeRequestDTO = api.graphQL.createMergeRequest(projectCoordinates, sourceBranch, targetBranch, title, description).getResultOrThrow()
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
    labelsLoader.cancel()
    membersLoader.cancel()
    _dataReloadSignal.tryEmit(Unit)
  }

  override suspend fun uploadFile(path: Path): String {
    val uploadRestDTO = withContext(cs.coroutineContext + Dispatchers.IO) {
      val filename = path.fileName.toString()
      val mimeType = Files.probeContentType(path) ?: "application/octet-stream"
      Files.newInputStream(path).use {
        api.rest.markdownUploadFile(projectCoordinates, filename, mimeType, it).body()
      }
    }
    GitLabStatistics.logFileUploadActionExecuted(project)
    return uploadRestDTO.markdown
  }

  override suspend fun uploadImage(image: BufferedImage): String {
    val uploadRestDTO = withContext(cs.coroutineContext + Dispatchers.IO) {
      val byteArray = ByteArrayOutputStream().use { outputStream ->
        ImageIO.write(image, "PNG", outputStream)
        outputStream.toByteArray()
      }
      ByteArrayInputStream(byteArray).use {
        api.rest.markdownUploadFile(projectCoordinates, "image.png", "image/png", it).body()
      }
    }
    GitLabStatistics.logFileUploadActionExecuted(project)
    return uploadRestDTO.markdown
  }

  override fun canUploadFile(): Boolean {
    return glMetadata != null && glMetadata.version >= GitLabVersion(15, 10)
  }

  private suspend fun getAllowsMultipleAssigneesPropertyFromNamespacePlan() = try {
    api.rest.getProjectNamespace(projectMapping.repository.projectPath.owner).body()?.plan?.let {
      it != GitLabPlan.FREE
    } ?: run {
      LOG.warn("Failed to find namespace for project ${projectMapping.repository}")
      null
    }
  }
  catch (ce: CancellationException) {
    throw ce
  }
  catch (e: Exception) {
    LOG.warn("Failed to load namespace for project ${projectMapping.repository}", e)
    null
  }

  private suspend fun getAllowsMultipleAssigneesPropertyFromIssueWidget(): Boolean {
    val widgetAssignees: WorkItemWidgetAssignees? = try {
      api.graphQL.createAllWorkItemsFlow(projectMapping.repository)
        .transformWhile { workItems ->
          val widget = workItems.find { workItem -> workItem.workItemType.name == WorkItemType.ISSUE_TYPE }
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
    }
    catch (ce: CancellationException) {
      throw ce
    }
    catch (e: Exception) {
      LOG.warn("Failed to load work item widgets for project ${projectMapping.repository}", e)
      return false
    }
    return widgetAssignees?.allowsMultipleAssignees ?: false
  }
}

private class BatchesLoader<T>(private val cs: CoroutineScope, private val batchesFlow: Flow<List<T>>) {
  private var flowAndScope: Pair<SharedFlow<BatchesLoadingState<T>>, CoroutineScope>? = null

  fun getBatches(): Flow<List<T>> {
    var currentPagesCount = 0
    return startLoading().transformWhile {
      if (it.pages.size > currentPagesCount) {
        emit(it.pages.subList(currentPagesCount, it.pages.size).flatten())
        currentPagesCount = it.pages.size
      }
      when (it) {
        is BatchesLoadingState.Loading -> true
        is BatchesLoadingState.Loaded -> false
        is BatchesLoadingState.Cancelled -> throw it.ce
        is BatchesLoadingState.Error -> throw it.error
      }
    }
  }

  @Synchronized
  private fun startLoading(): SharedFlow<BatchesLoadingState<T>> {
    flowAndScope?.run {
      return first
    }

    val sharingScope = cs.childScope(javaClass.name)
    val sharedFlow = flow {
      val loadedBatches = mutableListOf<List<T>>()
      try {
        batchesFlow.flowOn(Dispatchers.IO).collect { batch ->
          loadedBatches.add(batch)
          emit(BatchesLoadingState.Loading(loadedBatches.toList()))
        }
        // will never change anymore, so it's fine to emit as-is
        emit(BatchesLoadingState.Loaded(loadedBatches))
      }
      catch (ce: CancellationException) {
        emit(BatchesLoadingState.Cancelled(loadedBatches, ce))
        throw ce
      }
      catch (e: Exception) {
        emit(BatchesLoadingState.Error(loadedBatches, e))
      }
    }.shareIn(sharingScope, SharingStarted.Lazily, 1)
    flowAndScope = sharedFlow to sharingScope
    return sharedFlow
  }

  @Synchronized
  fun cancel() {
    flowAndScope?.second?.cancel()
    flowAndScope = null
  }

  private sealed class BatchesLoadingState<T>(val pages: List<List<T>>) {
    class Loading<T>(pages: List<List<T>>) : BatchesLoadingState<T>(pages)
    class Loaded<T>(pages: List<List<T>>) : BatchesLoadingState<T>(pages)
    class Error<T>(pages: List<List<T>>, val error: Exception) : BatchesLoadingState<T>(pages)
    class Cancelled<T>(pages: List<List<T>>, val ce: CancellationException) : BatchesLoadingState<T>(pages)
  }
}