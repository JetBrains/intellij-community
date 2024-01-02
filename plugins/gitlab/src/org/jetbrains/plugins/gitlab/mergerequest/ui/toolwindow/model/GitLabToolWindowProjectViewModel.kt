// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui.toolwindow.model

import com.github.benmanes.caffeine.cache.Caffeine
import com.intellij.collaboration.async.*
import com.intellij.collaboration.ui.icon.AsyncImageIconsProvider
import com.intellij.collaboration.ui.icon.CachingIconsProvider
import com.intellij.collaboration.ui.icon.IconsProvider
import com.intellij.collaboration.ui.toolwindow.ReviewToolwindowProjectViewModel
import com.intellij.collaboration.ui.toolwindow.ReviewToolwindowTabs
import com.intellij.collaboration.ui.toolwindow.ReviewToolwindowTabsStateHolder
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.platform.util.coroutines.childScope
import git4idea.remote.hosting.changesSignalFlow
import git4idea.repo.GitRemote
import git4idea.repo.GitRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.gitlab.GitLabProjectsManager
import org.jetbrains.plugins.gitlab.api.GitLabProjectConnection
import org.jetbrains.plugins.gitlab.api.dto.GitLabUserDTO
import org.jetbrains.plugins.gitlab.authentication.accounts.GitLabAccountManager
import org.jetbrains.plugins.gitlab.authentication.accounts.GitLabAccountViewModel
import org.jetbrains.plugins.gitlab.authentication.accounts.GitLabAccountViewModelImpl
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabMergeRequestDetails
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabMergeRequestState
import org.jetbrains.plugins.gitlab.mergerequest.diff.GitLabMergeRequestDiffViewModel
import org.jetbrains.plugins.gitlab.mergerequest.file.GitLabMergeRequestsFilesController
import org.jetbrains.plugins.gitlab.mergerequest.file.GitLabMergeRequestsFilesControllerImpl
import org.jetbrains.plugins.gitlab.mergerequest.ui.GitLabMergeRequestViewModels
import org.jetbrains.plugins.gitlab.mergerequest.ui.details.model.GitLabMergeRequestDetailsViewModel
import org.jetbrains.plugins.gitlab.mergerequest.ui.editor.GitLabMergeRequestEditorReviewViewModel
import org.jetbrains.plugins.gitlab.mergerequest.ui.filters.GitLabMergeRequestsFiltersHistoryModel
import org.jetbrains.plugins.gitlab.mergerequest.ui.filters.GitLabMergeRequestsFiltersViewModelImpl
import org.jetbrains.plugins.gitlab.mergerequest.ui.filters.GitLabMergeRequestsPersistentFiltersHistory
import org.jetbrains.plugins.gitlab.mergerequest.ui.list.GitLabMergeRequestsListViewModel
import org.jetbrains.plugins.gitlab.mergerequest.ui.list.GitLabMergeRequestsListViewModelImpl
import org.jetbrains.plugins.gitlab.mergerequest.ui.timeline.GitLabMergeRequestTimelineViewModel
import org.jetbrains.plugins.gitlab.mergerequest.ui.toolwindow.GitLabReviewTab
import org.jetbrains.plugins.gitlab.util.GitLabStatistics

private val LOG = logger<GitLabToolWindowProjectViewModel>()

internal class GitLabToolWindowProjectViewModel
private constructor(parentCs: CoroutineScope,
                    private val project: Project,
                    accountManager: GitLabAccountManager,
                    private val projectsManager: GitLabProjectsManager,
                    private val connection: GitLabProjectConnection,
                    val twVm: GitLabToolWindowViewModel)
  : ReviewToolwindowProjectViewModel<GitLabReviewTab, GitLabReviewTabViewModel> {

  private val cs = parentCs.childScope()

  val connectionId: String = connection.id
  override val projectName: @Nls String = connection.repo.repository.projectPath.name

  val defaultBranch: Deferred<String> = connection.projectData.defaultBranch

  private val mergeRequestsVms = Caffeine.newBuilder().build<String, SharedFlow<Result<GitLabMergeRequestViewModels>>> { iid ->
    connection.projectData.mergeRequests.getShared(iid)
      .transformConsecutiveSuccesses {
        mapScoped {
          GitLabMergeRequestViewModels(project, this, connection.projectData, it, this@GitLabToolWindowProjectViewModel, connection.currentUser)
        }
      }
      .shareIn(cs, SharingStarted.WhileSubscribed(0, 0), 1)
  }

  val filesController: GitLabMergeRequestsFilesController = GitLabMergeRequestsFilesControllerImpl(project, connection)

  val avatarIconProvider: IconsProvider<GitLabUserDTO> = CachingIconsProvider(AsyncImageIconsProvider(cs, connection.imageLoader))

  val accountVm: GitLabAccountViewModel =
    GitLabAccountViewModelImpl(project, cs, connection.account, accountManager)

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
      loaderSupplier = { filtersValue -> connection.projectData.mergeRequests.getListLoader(filtersValue.toSearchQuery()) }
    )
  }

  private val tabsHelper = ReviewToolwindowTabsStateHolder<GitLabReviewTab, GitLabReviewTabViewModel>()
  override val tabs: StateFlow<ReviewToolwindowTabs<GitLabReviewTab, GitLabReviewTabViewModel>> = tabsHelper.tabs.asStateFlow()

  fun showTab(tab: GitLabReviewTab, place: GitLabStatistics.ToolWindowOpenTabActionPlace) {
    tabsHelper.showTab(tab, {
      GitLabStatistics.logTwTabOpened(project, tab.toStatistics(), place)
      createVm(it)
    })
  }

  private fun createVm(tab: GitLabReviewTab): GitLabReviewTabViewModel = when (tab) {
    is GitLabReviewTab.ReviewSelected ->
      GitLabReviewTabViewModel.Details(cs, tab.mrIid, getDetailsViewModel(tab.mrIid))
    GitLabReviewTab.NewMergeRequest -> GitLabReviewTabViewModel.CreateMergeRequest(
      project, cs, projectsManager, connection.projectData, avatarIconProvider,
      openReviewTabAction = { mrIid ->
        tabsHelper.showTabInstead(GitLabReviewTab.NewMergeRequest, GitLabReviewTab.ReviewSelected(mrIid), {
          GitLabStatistics.logTwTabOpened(project, tab.toStatistics(), GitLabStatistics.ToolWindowOpenTabActionPlace.CREATION)
          createVm(it)
        })
      },
      onReviewCreated = {
        cs.launchNow {
          mergeRequestCreatedSignal.emit(Unit)
        }
      }
    )
  }

  override fun selectTab(tab: GitLabReviewTab?) = tabsHelper.select(tab)
  override fun closeTab(tab: GitLabReviewTab) = tabsHelper.close(tab)

  fun showCreationTab(place: GitLabStatistics.ToolWindowOpenTabActionPlace) {
    showTab(GitLabReviewTab.NewMergeRequest, place)
  }

  private val mergeRequestCreatedSignal: MutableSharedFlow<Unit> = MutableSharedFlow()

  val mergeRequestOnCurrentBranch: Flow<String?> = run {
    val projectMapping = connection.repo
    val remote = projectMapping.remote.remote
    val gitRepo = projectMapping.remote.repository
    val targetProjectPath = projectMapping.repository.projectPath.fullPath()

    gitRepo.changesSignalFlow().withInitial(Unit)
      .map { findCurrentRemoteBranch(gitRepo, remote) }
      .distinctUntilChanged()
      .combine(mergeRequestCreatedSignal.withInitial(Unit)) { currentRemoteBranch, _ ->
        currentRemoteBranch ?: return@combine null
        try {
          findOpenReviewIdByBranch(connection, currentRemoteBranch, targetProjectPath)
        }
        catch (ce: CancellationException) {
          null
        }
        catch (e: Exception) {
          LOG.warn("Could not lookup a merge request for current branch", e)
          null
        }
      }
  }

  @OptIn(ExperimentalCoroutinesApi::class)
  val currentMergeRequestReviewVm: Flow<GitLabMergeRequestEditorReviewViewModel?> =
    mergeRequestOnCurrentBranch.distinctUntilChanged().flatMapLatest { id ->
      if (id == null) flowOf(null) else mergeRequestsVms[id].map { it.getOrNull()?.editorReviewVm }
    }

  private fun getDetailsViewModel(mrIid: String): Flow<Result<GitLabMergeRequestDetailsViewModel>> =
    mergeRequestsVms[mrIid].mapCatching { it.detailsVm }

  fun getTimelineViewModel(mrIid: String): Flow<Result<GitLabMergeRequestTimelineViewModel>> =
    mergeRequestsVms[mrIid].mapCatching { it.timelineVm }

  fun getDiffViewModel(mrIid: String): Flow<Result<GitLabMergeRequestDiffViewModel>> =
    mergeRequestsVms[mrIid].mapCatching { it.diffVm }

  fun findMergeRequestDetails(mrIid: String): GitLabMergeRequestDetails? =
    connection.projectData.mergeRequests.findCachedDetails(mrIid)

  init {
    cs.launchNow {
      try {
        awaitCancellation()
      }
      finally {
        withContext(NonCancellable + ModalityState.any().asContextElement()) {
          filesController.closeAllFiles()
        }
      }
    }
  }

  companion object {
    internal fun CoroutineScope.GitLabToolWindowProjectViewModel(project: Project,
                                                                 accountManager: GitLabAccountManager,
                                                                 projectsManager: GitLabProjectsManager,
                                                                 connection: GitLabProjectConnection,
                                                                 twVm: GitLabToolWindowViewModel) =
      GitLabToolWindowProjectViewModel(this, project, accountManager, projectsManager, connection, twVm)

    private fun GitLabReviewTab.toStatistics(): GitLabStatistics.ToolWindowTabType {
      return when (this) {
        GitLabReviewTab.NewMergeRequest -> GitLabStatistics.ToolWindowTabType.CREATION
        is GitLabReviewTab.ReviewSelected -> GitLabStatistics.ToolWindowTabType.DETAILS
      }
    }
  }
}

private fun findCurrentRemoteBranch(gitRepo: GitRepository, remote: GitRemote): String? {
  val currentBranch = gitRepo.currentBranch ?: return null
  return gitRepo.branchTrackInfos.find { it.localBranch == currentBranch && it.remote == remote }
    ?.remoteBranch?.nameForRemoteOperations
}

private suspend fun findOpenReviewIdByBranch(
  connection: GitLabProjectConnection,
  currentRemoteBranch: String,
  targetProjectPath: String
): String? {
  return connection.projectData.mergeRequests.findByBranches(GitLabMergeRequestState.OPENED, currentRemoteBranch).find {
    it.targetProject.fullPath == targetProjectPath && it.sourceProject?.fullPath == targetProjectPath
  }?.iid
}