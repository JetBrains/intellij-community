// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui

import com.github.benmanes.caffeine.cache.Caffeine
import com.intellij.collaboration.async.mapCatching
import com.intellij.collaboration.async.mapScoped
import com.intellij.collaboration.async.transformConsecutiveSuccesses
import com.intellij.collaboration.async.withInitial
import com.intellij.collaboration.ui.icon.AsyncImageIconsProvider
import com.intellij.collaboration.ui.icon.CachingIconsProvider
import com.intellij.collaboration.ui.icon.IconsProvider
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.platform.util.coroutines.childScope
import com.intellij.util.concurrency.annotations.RequiresEdt
import git4idea.remote.hosting.findHostedRemoteBranchTrackedByCurrent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.gitlab.GitLabProjectsManager
import org.jetbrains.plugins.gitlab.api.GitLabProjectConnection
import org.jetbrains.plugins.gitlab.api.dto.GitLabUserDTO
import org.jetbrains.plugins.gitlab.authentication.accounts.GitLabAccountManager
import org.jetbrains.plugins.gitlab.authentication.accounts.GitLabAccountViewModel
import org.jetbrains.plugins.gitlab.authentication.accounts.GitLabAccountViewModelImpl
import org.jetbrains.plugins.gitlab.data.GitLabImageLoader
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabMergeRequestDetails
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabMergeRequestState
import org.jetbrains.plugins.gitlab.mergerequest.diff.GitLabMergeRequestDiffViewModel
import org.jetbrains.plugins.gitlab.mergerequest.ui.details.model.GitLabMergeRequestDetailsViewModel
import org.jetbrains.plugins.gitlab.mergerequest.ui.editor.GitLabMergeRequestEditorReviewViewModel
import org.jetbrains.plugins.gitlab.mergerequest.ui.filters.GitLabMergeRequestsFiltersHistoryModel
import org.jetbrains.plugins.gitlab.mergerequest.ui.filters.GitLabMergeRequestsFiltersViewModelImpl
import org.jetbrains.plugins.gitlab.mergerequest.ui.filters.GitLabMergeRequestsPersistentFiltersHistory
import org.jetbrains.plugins.gitlab.mergerequest.ui.list.GitLabMergeRequestsListViewModel
import org.jetbrains.plugins.gitlab.mergerequest.ui.list.GitLabMergeRequestsListViewModelImpl
import org.jetbrains.plugins.gitlab.mergerequest.ui.timeline.GitLabMergeRequestTimelineViewModel
import org.jetbrains.plugins.gitlab.util.GitLabStatistics

@ApiStatus.Internal
interface GitLabConnectedProjectViewModel {
  val connectionId: String
  val avatarIconProvider: IconsProvider<GitLabUserDTO>
  val accountVm: GitLabAccountViewModel
  val listVm: GitLabMergeRequestsListViewModel
  val currentMergeRequestReviewVm: Flow<GitLabMergeRequestEditorReviewViewModel?>
  val imageLoader: GitLabImageLoader
  fun findMergeRequestDetails(mrIid: String): GitLabMergeRequestDetails?
  fun reloadMergeRequestDetails(mergeRequestId: String)
  fun getDiffViewModel(mrIid: String): Flow<Result<GitLabMergeRequestDiffViewModel>>
  fun getTimelineViewModel(mrIid: String): Flow<Result<GitLabMergeRequestTimelineViewModel>>
  fun getDetailsViewModel(mrIid: String): Flow<Result<GitLabMergeRequestDetailsViewModel>>
  fun openMergeRequestDetails(mrIid: String?, place: GitLabStatistics.ToolWindowOpenTabActionPlace, focus: Boolean = false)
  fun viewMergeRequestList()
  fun closeNewMergeRequestDetails()

  @RequiresEdt
  fun openMergeRequestTimeline(mrIid: String, focus: Boolean)
}

@ApiStatus.Internal
abstract class GitLabConnectedProjectViewModelBase(
  parentCs: CoroutineScope,
  private val project: Project,
  private val connection: GitLabProjectConnection,
  accountManager: GitLabAccountManager,
  projectsManager: GitLabProjectsManager,
) : GitLabConnectedProjectViewModel {
  protected val cs: CoroutineScope = parentCs.childScope(javaClass.name)

  override val connectionId: String = connection.id

  override val accountVm: GitLabAccountViewModel = GitLabAccountViewModelImpl(project, cs, connection.account, accountManager)

  override val avatarIconProvider: IconsProvider<GitLabUserDTO> = CachingIconsProvider(AsyncImageIconsProvider(cs, connection.imageLoader))

  override val imageLoader: GitLabImageLoader = connection.imageLoader

  private val projectName: @Nls String = connection.repo.repository.projectPath.name

  override val listVm: GitLabMergeRequestsListViewModel = run {
    val filterVm = GitLabMergeRequestsFiltersViewModelImpl(
      cs,
      project,
      currentUser = connection.currentUser,
      historyModel = GitLabMergeRequestsFiltersHistoryModel(project.service<GitLabMergeRequestsPersistentFiltersHistory>()),
      avatarIconsProvider = avatarIconProvider,
      projectData = connection.projectData
    )

    GitLabMergeRequestsListViewModelImpl(
      cs,
      filterVm = filterVm,
      repository = projectName,
      avatarIconsProvider = avatarIconProvider,
      tokenRefreshFlow = connection.tokenRefreshFlow,
      loaderSupplier = { cs, filtersValue -> connection.projectData.mergeRequests.getListLoaderIn(cs, filtersValue.toSearchQuery()) }
    )
  }

  private val mergeRequestsVms = Caffeine.newBuilder().build<String, SharedFlow<Result<GitLabMergeRequestViewModels>>> { iid ->
    connection.projectData.mergeRequests.getShared(iid)
      .transformConsecutiveSuccesses {
        mapScoped {
          GitLabMergeRequestViewModels(project, this, connection.projectData, avatarIconProvider,
                                       connection.imageLoader, it, connection.currentUser,
                                       ::openMergeRequestDetails, ::openMergeRequestTimeline, ::openMergeRequestDiff)
        }
      }
      .shareIn(cs, SharingStarted.WhileSubscribed(0, 0), 1)
  }

  protected val mergeRequestCreatedSignal: MutableSharedFlow<Unit> = MutableSharedFlow()

  private val mergeRequestOnCurrentBranch: Flow<String?> =
    projectsManager.findHostedRemoteBranchTrackedByCurrent(connection.repo.gitRepository)
      .combine(mergeRequestCreatedSignal.withInitial(Unit)) { repoAndBranch, _ ->
        val (targetRepo, branch) = repoAndBranch ?: return@combine null
        try {
          findOpenReviewIdByBranch(connection, branch.nameForRemoteOperations, targetRepo.repository.projectPath.fullPath())
        }
        catch (ce: CancellationException) {
          null
        }
        catch (e: Exception) {
          LOG.warn("Could not lookup a merge request for current branch", e)
          null
        }
      }

  private suspend fun findOpenReviewIdByBranch(
    connection: GitLabProjectConnection,
    currentRemoteBranch: String,
    targetProjectPath: String,
  ): String? {
    return connection.projectData.mergeRequests.findByBranches(GitLabMergeRequestState.OPENED, currentRemoteBranch).find {
      it.targetProject.fullPath == targetProjectPath && it.sourceProject?.fullPath == targetProjectPath
    }?.iid
  }

  @OptIn(ExperimentalCoroutinesApi::class)
  override val currentMergeRequestReviewVm: Flow<GitLabMergeRequestEditorReviewViewModel?> =
    mergeRequestOnCurrentBranch.distinctUntilChanged().flatMapLatest { id ->
      if (id == null) flowOf(null) else mergeRequestsVms[id].map { it.getOrNull()?.editorReviewVm }
    }

  override fun getDetailsViewModel(mrIid: String): Flow<Result<GitLabMergeRequestDetailsViewModel>> =
    mergeRequestsVms[mrIid].mapCatching { it.detailsVm }

  override fun getTimelineViewModel(mrIid: String): Flow<Result<GitLabMergeRequestTimelineViewModel>> =
    mergeRequestsVms[mrIid].mapCatching { it.timelineVm }

  override fun getDiffViewModel(mrIid: String): Flow<Result<GitLabMergeRequestDiffViewModel>> =
    mergeRequestsVms[mrIid].mapCatching { it.diffVm }

  override fun findMergeRequestDetails(mrIid: String): GitLabMergeRequestDetails? =
    connection.projectData.mergeRequests.findCachedDetails(mrIid)

  override fun reloadMergeRequestDetails(mergeRequestId: String) {
    cs.launch {
      connection.projectData.mergeRequests.reloadMergeRequest(mergeRequestId)
    }
  }

  @RequiresEdt
  abstract fun openMergeRequestDiff(mrIid: String, focus: Boolean)

  companion object {
    private val LOG = logger<GitLabConnectedProjectViewModelBase>()
  }
}