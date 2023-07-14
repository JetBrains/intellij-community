// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui

import com.github.benmanes.caffeine.cache.Caffeine
import com.intellij.collaboration.ui.icon.AsyncImageIconsProvider
import com.intellij.collaboration.ui.icon.CachingIconsProvider
import com.intellij.collaboration.ui.icon.IconsProvider
import com.intellij.collaboration.ui.toolwindow.ReviewToolwindowProjectViewModel
import com.intellij.collaboration.ui.toolwindow.ReviewToolwindowTabs
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.util.awaitCancellationAndInvoke
import com.intellij.util.childScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.gitlab.api.GitLabProjectConnection
import org.jetbrains.plugins.gitlab.api.dto.GitLabUserDTO
import org.jetbrains.plugins.gitlab.authentication.accounts.GitLabAccount
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabLazyProject
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabMergeRequestId
import org.jetbrains.plugins.gitlab.mergerequest.diff.GitLabMergeRequestDiffBridge
import org.jetbrains.plugins.gitlab.mergerequest.file.GitLabMergeRequestsFilesController
import org.jetbrains.plugins.gitlab.mergerequest.file.GitLabMergeRequestsFilesControllerImpl
import org.jetbrains.plugins.gitlab.mergerequest.ui.filters.GitLabMergeRequestsFiltersHistoryModel
import org.jetbrains.plugins.gitlab.mergerequest.ui.filters.GitLabMergeRequestsFiltersViewModelImpl
import org.jetbrains.plugins.gitlab.mergerequest.ui.filters.GitLabMergeRequestsPersistentFiltersHistory
import org.jetbrains.plugins.gitlab.mergerequest.ui.list.GitLabMergeRequestsListViewModel
import org.jetbrains.plugins.gitlab.mergerequest.ui.list.GitLabMergeRequestsListViewModelImpl
import org.jetbrains.plugins.gitlab.mergerequest.ui.toolwindow.GitLabReviewTab

internal class GitLabToolWindowProjectViewModel
private constructor(parentCs: CoroutineScope, private val project: Project, connection: GitLabProjectConnection)
  : ReviewToolwindowProjectViewModel<GitLabReviewTab, GitLabReviewTabViewModel> {

  private val cs = parentCs.childScope()

  val connectionId: String = connection.id
  override val projectName: @Nls String = connection.repo.repository.projectPath.name

  val currentUser: GitLabUserDTO = connection.currentUser
  val projectData: GitLabLazyProject = connection.projectData

  private val diffBridgeStore = Caffeine.newBuilder()
    .weakValues()
    .build<GitLabMergeRequestId.Simple, GitLabMergeRequestDiffBridge>()

  val filesController: GitLabMergeRequestsFilesController = GitLabMergeRequestsFilesControllerImpl(project, connection)

  val avatarIconProvider: IconsProvider<GitLabUserDTO> = CachingIconsProvider(AsyncImageIconsProvider(cs, connection.imageLoader))

  // should not be here
  val account: GitLabAccount = connection.account

  override val listVm: GitLabMergeRequestsListViewModel = run {
    val filterVm = GitLabMergeRequestsFiltersViewModelImpl(
      cs,
      project,
      currentUser = currentUser,
      historyModel = GitLabMergeRequestsFiltersHistoryModel(project.service<GitLabMergeRequestsPersistentFiltersHistory>()),
      avatarIconsProvider = avatarIconProvider,
      projectData = projectData
    )

    GitLabMergeRequestsListViewModelImpl(
      cs,
      filterVm = filterVm,
      repository = projectName,
      avatarIconsProvider = avatarIconProvider,
      tokenRefreshFlow = connection.tokenRefreshFlow,
      loaderSupplier = { filtersValue -> projectData.mergeRequests.getListLoader(filtersValue.toSearchQuery()) }
    )
  }

  private val _tabs = MutableStateFlow<ReviewToolwindowTabs<GitLabReviewTab, GitLabReviewTabViewModel>>(
    ReviewToolwindowTabs(emptyMap(), null)
  )
  override val tabs: StateFlow<ReviewToolwindowTabs<GitLabReviewTab, GitLabReviewTabViewModel>> = _tabs.asStateFlow()

  private val tabsGuard = Mutex()

  fun show(mr: GitLabMergeRequestId) {
    showTab(GitLabReviewTab.ReviewSelected(mr))
  }

  private fun showTab(tab: GitLabReviewTab) {
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
    is GitLabReviewTab.ReviewSelected -> GitLabReviewTabViewModel.Details(project, cs, currentUser, projectData, tab.reviewId)
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
      tabsGuard.withLock {
        val current = _tabs.value
        val currentVm = current.tabs[tab]
        if (currentVm != null) {
          currentVm.destroy()
          _tabs.value = current.copy(current.tabs - tab, null)
        }
      }
    }
  }

  fun getDiffBridge(mr: GitLabMergeRequestId): GitLabMergeRequestDiffBridge =
    diffBridgeStore.get(GitLabMergeRequestId.Simple(mr)) {
      GitLabMergeRequestDiffBridge()
    }

  init {
    cs.awaitCancellationAndInvoke { filesController.closeAllFiles() }
  }

  companion object {
    internal fun CoroutineScope.GitLabToolWindowProjectViewModel(project: Project, connection: GitLabProjectConnection) =
      GitLabToolWindowProjectViewModel(this, project, connection)
  }
}