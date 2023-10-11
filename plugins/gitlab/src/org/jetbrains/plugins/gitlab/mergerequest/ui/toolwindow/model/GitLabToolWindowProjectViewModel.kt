// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui.toolwindow.model

import com.github.benmanes.caffeine.cache.Caffeine
import com.intellij.collaboration.async.*
import com.intellij.collaboration.ui.icon.AsyncImageIconsProvider
import com.intellij.collaboration.ui.icon.CachingIconsProvider
import com.intellij.collaboration.ui.icon.IconsProvider
import com.intellij.collaboration.ui.toolwindow.ReviewToolwindowProjectViewModel
import com.intellij.collaboration.ui.toolwindow.ReviewToolwindowTabs
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.util.awaitCancellationAndInvoke
import com.intellij.util.childScope
import git4idea.remote.hosting.changesSignalFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.gitlab.GitLabProjectsManager
import org.jetbrains.plugins.gitlab.api.GitLabProjectConnection
import org.jetbrains.plugins.gitlab.api.dto.GitLabUserDTO
import org.jetbrains.plugins.gitlab.authentication.accounts.GitLabAccountManager
import org.jetbrains.plugins.gitlab.authentication.accounts.GitLabAccountViewModel
import org.jetbrains.plugins.gitlab.authentication.accounts.GitLabAccountViewModelImpl
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabMergeRequestDetails
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

  private val mergeRequestsVms = Caffeine.newBuilder().build<String, SharedFlow<Result<GitLabMergeRequestViewModels>>> { iid ->
    val projectData = connection.projectData
    projectData.mergeRequests.getShared(iid)
      .throwFailure()
      .mapScoped {
        GitLabMergeRequestViewModels(project, this, projectData, it, this@GitLabToolWindowProjectViewModel, connection.currentUser)
      }
      .asResultFlow()
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

  private val _tabs = MutableStateFlow<ReviewToolwindowTabs<GitLabReviewTab, GitLabReviewTabViewModel>>(
    ReviewToolwindowTabs(emptyMap(), null)
  )
  override val tabs: StateFlow<ReviewToolwindowTabs<GitLabReviewTab, GitLabReviewTabViewModel>> = _tabs.asStateFlow()

  private val tabsGuard = Mutex()

  fun showTab(tab: GitLabReviewTab) {
    cs.launch {
      tabsGuard.withLock {
        val current = _tabs.value
        val currentVm = current.tabs[tab]
        if (currentVm == null || !tab.reuseTabOnRequest) {
          currentVm?.destroy()
          val tabVm = createVm(tab)
          _tabs.value = current.copy(current.tabs + (tab to tabVm), tab)
        }
        else {
          _tabs.value = current.copy(selectedTab = tab)
        }
      }
    }
  }

  private fun createVm(tab: GitLabReviewTab): GitLabReviewTabViewModel = when (tab) {
    is GitLabReviewTab.ReviewSelected ->
      GitLabReviewTabViewModel.Details(cs, tab.mrIid, getDetailsViewModel(tab.mrIid))
    GitLabReviewTab.NewMergeRequest -> GitLabReviewTabViewModel.CreateMergeRequest(
      project, cs, projectsManager, connection.projectData, avatarIconProvider,
      openReviewTabAction = { mrIid ->
        closeTabAsync(GitLabReviewTab.NewMergeRequest)
        showTab(GitLabReviewTab.ReviewSelected(mrIid))
      }
    )
  }

  override fun selectTab(tab: GitLabReviewTab?) {
    cs.launch {
      tabsGuard.withLock {
        _tabs.update {
          it.copy(selectedTab = tab)
        }
      }
    }
  }

  override fun closeTab(tab: GitLabReviewTab) {
    cs.launch {
      closeTabAsync(tab)
    }
  }

  private suspend fun closeTabAsync(tab: GitLabReviewTab) {
    tabsGuard.withLock {
      val current = _tabs.value
      val currentVm = current.tabs[tab]
      if (currentVm != null) {
        currentVm.destroy()
        _tabs.value = current.copy(current.tabs - tab, null)
      }
    }
  }

  fun createMergeRequest() {
    cs.launch {
      showTab(GitLabReviewTab.NewMergeRequest)
    }
  }

  @OptIn(ExperimentalCoroutinesApi::class)
  val mergeRequestOnCurrentBranch: Flow<String?> = run {
    val remote = connection.repo.remote.remote
    val gitRepo = connection.repo.remote.repository
    gitRepo.changesSignalFlow().withInitial(Unit).map {
      val currentBranch = gitRepo.currentBranch ?: return@map null
      gitRepo.branchTrackInfos.find { it.localBranch == currentBranch && it.remote == remote }
        ?.remoteBranch?.nameForRemoteOperations
    }.distinctUntilChanged().mapLatest { currentRemoteBranch ->
      currentRemoteBranch?.let {
        connection.projectData.mergeRequests.findByBranch(it).firstOrNull()
      }
    }.catch {
      LOG.warn("Could not lookup a merge request for current branch", it)
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
    cs.awaitCancellationAndInvoke { filesController.closeAllFiles() }
  }

  companion object {
    internal fun CoroutineScope.GitLabToolWindowProjectViewModel(project: Project,
                                                                 accountManager: GitLabAccountManager,
                                                                 projectsManager: GitLabProjectsManager,
                                                                 connection: GitLabProjectConnection,
                                                                 twVm: GitLabToolWindowViewModel) =
      GitLabToolWindowProjectViewModel(this, project, accountManager, projectsManager, connection, twVm)
  }
}