// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.data

import com.intellij.collaboration.api.page.ApiPageUtil
import com.intellij.collaboration.async.BatchesLoader
import com.intellij.collaboration.util.CodeReviewDomainEntity
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.platform.util.coroutines.childScope
import com.intellij.util.containers.nullize
import git4idea.remote.GitRemoteUrlCoordinates
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.transformWhile
import org.jetbrains.plugins.gitlab.api.GitLabApi
import org.jetbrains.plugins.gitlab.api.GitLabGidData
import org.jetbrains.plugins.gitlab.api.GitLabGraphQLMutationException
import org.jetbrains.plugins.gitlab.api.GitLabProjectCoordinates
import org.jetbrains.plugins.gitlab.api.GitLabServerMetadata
import org.jetbrains.plugins.gitlab.api.GitLabVersion
import org.jetbrains.plugins.gitlab.api.data.GitLabPlan
import org.jetbrains.plugins.gitlab.api.dto.GitLabProjectDTO
import org.jetbrains.plugins.gitlab.api.dto.GitLabUserDTO
import org.jetbrains.plugins.gitlab.api.dto.GitLabWorkItemDTO.GitLabWidgetDTO.WorkItemWidgetAssignees
import org.jetbrains.plugins.gitlab.api.dto.GitLabWorkItemDTO.WorkItemType
import org.jetbrains.plugins.gitlab.api.request.createAllProjectLabelsFlow
import org.jetbrains.plugins.gitlab.api.request.createAllWorkItemsFlow
import org.jetbrains.plugins.gitlab.api.request.getProjectNamespace
import org.jetbrains.plugins.gitlab.api.request.getProjectUsers
import org.jetbrains.plugins.gitlab.api.request.getProjectUsersURI
import org.jetbrains.plugins.gitlab.mergerequest.api.dto.GitLabMergeRequestDTO
import org.jetbrains.plugins.gitlab.mergerequest.api.request.createMergeRequest
import org.jetbrains.plugins.gitlab.mergerequest.api.request.loadMergeRequest
import org.jetbrains.plugins.gitlab.upload.markdownUploadFile
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
  val projectCoordinates: GitLabProjectCoordinates
  val gitRemote: GitRemoteUrlCoordinates
  val projectId: String

  val dataReloadSignal: SharedFlow<Unit>
  val mergeRequests: GitLabProjectMergeRequestsStore

  suspend fun getEmojis(): List<GitLabReaction>

  fun getLabelsBatches(): Flow<List<GitLabLabel>>
  fun getMembersBatches(): Flow<List<GitLabUserDTO>>

  val defaultBranch: String?
  suspend fun isMultipleAssigneesAllowed(): Boolean
  suspend fun isMultipleReviewersAllowed(): Boolean

  /**
   * Creates a merge request on the GitLab server and returns a DTO containing the merge request
   * once the merge request was successfully initialized on server.
   * The reason for this wait is that GitLab might take a few moments to process the merge request
   * before returning one that can be displayed in the IDE in a useful way.
   *
   * @param reviewers List of reviewer user DTOs to assign to the merge request
   * Note: this parameter has different behavior depending on [isMultipleReviewersAllowed] -
   * either sets only one reviewer from the list (the last one) or sets all reviewers from the list
   */
  suspend fun createMergeRequestAndAwaitCompletion(
    sourceBranch: String,
    targetBranch: String,
    title: String,
    description: String?,
    reviewers: List<GitLabUserDTO> = emptyList(),
    assignees: List<GitLabUserDTO> = emptyList(),
    labels: List<GitLabLabel> = emptyList(),
  ): GitLabMergeRequestDTO

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
  private val initialData: GitLabProjectDTO,
  private val currentUser: GitLabUserDTO,
  private val tokenRefreshFlow: Flow<Unit>,
  override val projectCoordinates: GitLabProjectCoordinates,
  override val gitRemote: GitRemoteUrlCoordinates,
) : GitLabProject {

  private val cs = parentCs.childScope(javaClass.name)

  override val projectId: String = initialData.id.guessRestId()

  private val _dataReloadSignal = MutableSharedFlow<Unit>(replay = 1)
  override val dataReloadSignal: SharedFlow<Unit> = _dataReloadSignal.asSharedFlow()

  private val emojisRequest = cs.async(start = CoroutineStart.LAZY) {
    serviceAsync<GitLabEmojiService>().emojis.await().map { GitLabReactionImpl(it) }  }

  private val multipleAssigneesAllowedFallbackRequest = cs.async(Dispatchers.IO, start = CoroutineStart.LAZY) {
    loadMultipleAssigneesAllowedFallback()
  }

  override val mergeRequests: GitLabProjectMergeRequestsStore by lazy {
    CachingGitLabProjectMergeRequestsStore(project, cs, api, glMetadata, currentUser,
                                           tokenRefreshFlow, projectCoordinates, projectId, gitRemote)
  }

  private val labelsLoader = BatchesLoader(cs,
                                           api.graphQL.createAllProjectLabelsFlow(projectCoordinates.projectPath).map { labels ->
                                             labels.map {
                                               GitLabLabel(it.title, it.color)
                                             }
                                           })
  private val membersLoader = BatchesLoader(cs,
                                            ApiPageUtil.createPagesFlowByLinkHeader(api.rest.getProjectUsersURI(projectId)) {
                                              api.rest.getProjectUsers(it)
                                            }.map { response -> response.body().map(GitLabUserDTO::fromRestDTO) })

  override suspend fun getEmojis(): List<GitLabReaction> = emojisRequest.await()
  override val defaultBranch: String? = initialData.repository?.rootRef

  override suspend fun isMultipleAssigneesAllowed(): Boolean {
    return initialData.allowsMultipleMergeRequestAssignees
           ?: multipleAssigneesAllowedFallbackRequest.await()
           ?: false
  }

  override suspend fun isMultipleReviewersAllowed(): Boolean {
    return initialData.allowsMultipleMergeRequestReviewers
           ?: multipleAssigneesAllowedFallbackRequest.await()
           ?: false
  }

  override fun getLabelsBatches(): Flow<List<GitLabLabel>> = labelsLoader.getBatches()
  override fun getMembersBatches(): Flow<List<GitLabUserDTO>> = membersLoader.getBatches()

  private suspend fun loadMultipleAssigneesAllowedFallback(): Boolean? {
    val fromPlan = getAllowsMultipleAssigneesPropertyFromNamespacePlan()
    if (fromPlan != null) {
      return fromPlan
    }

    if (glMetadata != null && glMetadata.version >= GitLabVersion(15, 2)) {
      return getAllowsMultipleAssigneesPropertyFromIssueWidget()
    }

    return null
  }

  @Throws(GitLabGraphQLMutationException::class)
  override suspend fun createMergeRequestAndAwaitCompletion(
    sourceBranch: String,
    targetBranch: String,
    title: String,
    description: String?,
    reviewers: List<GitLabUserDTO>,
    assignees: List<GitLabUserDTO>,
    labels: List<GitLabLabel>,
  ): GitLabMergeRequestDTO {
    return cs.async(Dispatchers.IO) {
      val reviewerIds = reviewers.nullize()?.map { GitLabGidData(it.id).guessRestId() }
      val assigneeIds = assignees.nullize()?.map { GitLabGidData(it.id).guessRestId() }
      val labelTitles = labels.nullize()?.map { it.title }
      val iid = api.rest.createMergeRequest(
        projectId,
        sourceBranch,
        targetBranch,
        title,
        description,
        reviewerIds,
        assigneeIds,
        labelTitles
      ).body().iid
      val attempts = GitLabRegistry.getRequestPollingAttempts()
      repeat(attempts) {
        val data = api.graphQL.loadMergeRequest(projectCoordinates.projectPath, iid).body()
        if (data?.diffRefs != null) {
          return@async data
        }
        delay(GitLabRegistry.getRequestPollingIntervalMillis().toLong())
      }
      error("Merge request $iid was created but the data was not loaded within $attempts attempts.")
    }.await()
  }

  override fun reloadData() {
    labelsLoader.cancel()
    membersLoader.cancel()
    _dataReloadSignal.tryEmit(Unit)
  }

  override suspend fun uploadFile(path: Path): String {
    val uploadRestDTO = cs.async(Dispatchers.IO) {
      val filename = path.fileName.toString()
      val mimeType = Files.probeContentType(path) ?: "application/octet-stream"
      Files.newInputStream(path).use {
        api.rest.markdownUploadFile(projectId, filename, mimeType, it).body()
      }
    }.await()
    GitLabStatistics.logFileUploadActionExecuted(project)
    return uploadRestDTO.markdown
  }

  override suspend fun uploadImage(image: BufferedImage): String {
    val uploadRestDTO = cs.async(Dispatchers.IO) {
      val byteArray = ByteArrayOutputStream().use { outputStream ->
        ImageIO.write(image, "PNG", outputStream)
        outputStream.toByteArray()
      }
      ByteArrayInputStream(byteArray).use {
        api.rest.markdownUploadFile(projectId, "image.png", "image/png", it).body()
      }
    }.await()
    GitLabStatistics.logFileUploadActionExecuted(project)
    return uploadRestDTO.markdown
  }

  override fun canUploadFile(): Boolean {
    return glMetadata != null && glMetadata.version >= GitLabVersion(15, 10)
  }

  private suspend fun getAllowsMultipleAssigneesPropertyFromNamespacePlan() = try {
    api.rest.getProjectNamespace(projectCoordinates.projectPath.owner).body()?.plan?.let {
      it != GitLabPlan.FREE
    } ?: run {
      LOG.warn("Failed to find namespace for project ${projectCoordinates.projectPath.fullPath()}")
      null
    }
  }
  catch (ce: CancellationException) {
    throw ce
  }
  catch (e: Exception) {
    LOG.warn("Failed to load namespace for project ${projectCoordinates.projectPath.fullPath()}", e)
    null
  }

  private suspend fun getAllowsMultipleAssigneesPropertyFromIssueWidget(): Boolean {
    val widgetAssignees: WorkItemWidgetAssignees? = try {
      api.graphQL.createAllWorkItemsFlow(projectCoordinates.projectPath)
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
      LOG.warn("Failed to load work item widgets for project ${projectCoordinates.projectPath}", e)
      return false
    }
    return widgetAssignees?.allowsMultipleAssignees ?: false
  }
}