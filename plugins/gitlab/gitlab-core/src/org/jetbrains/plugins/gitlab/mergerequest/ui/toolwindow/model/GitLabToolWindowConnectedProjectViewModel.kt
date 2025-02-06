// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui.toolwindow.model

import com.intellij.collaboration.async.launchNow
import com.intellij.collaboration.ui.toolwindow.ReviewToolwindowProjectViewModel
import com.intellij.collaboration.ui.toolwindow.ReviewToolwindowTabs
import com.intellij.collaboration.ui.toolwindow.ReviewToolwindowTabsStateHolder
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.project.Project
import com.intellij.util.concurrency.annotations.RequiresEdt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.gitlab.GitLabProjectsManager
import org.jetbrains.plugins.gitlab.api.GitLabProjectConnection
import org.jetbrains.plugins.gitlab.authentication.accounts.GitLabAccountManager
import org.jetbrains.plugins.gitlab.mergerequest.file.GitLabMergeRequestsFilesController
import org.jetbrains.plugins.gitlab.mergerequest.file.GitLabMergeRequestsFilesControllerImpl
import org.jetbrains.plugins.gitlab.mergerequest.ui.GitLabConnectedProjectViewModelBase
import org.jetbrains.plugins.gitlab.mergerequest.ui.toolwindow.GitLabReviewTab
import org.jetbrains.plugins.gitlab.util.GitLabStatistics

internal class GitLabToolWindowConnectedProjectViewModel(
  parentCs: CoroutineScope,
  private val project: Project,
  accountManager: GitLabAccountManager,
  private val projectsManager: GitLabProjectsManager,
  private val connection: GitLabProjectConnection,
  private val activate: () -> Unit,
) : ReviewToolwindowProjectViewModel<GitLabReviewTab, GitLabReviewTabViewModel>,
    GitLabConnectedProjectViewModelBase(parentCs, project, connection, accountManager, projectsManager) {

  private val filesController: GitLabMergeRequestsFilesController = GitLabMergeRequestsFilesControllerImpl(project, connection)

  override val projectName: @Nls String = connection.repo.repository.projectPath.name

  private val tabsHelper = ReviewToolwindowTabsStateHolder<GitLabReviewTab, GitLabReviewTabViewModel>()
  override val tabs: StateFlow<ReviewToolwindowTabs<GitLabReviewTab, GitLabReviewTabViewModel>> = tabsHelper.tabs.asStateFlow()

  private fun showTab(tab: GitLabReviewTab, place: GitLabStatistics.ToolWindowOpenTabActionPlace) {
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

  override fun openMergeRequestDetails(mrIid: String?, place: GitLabStatistics.ToolWindowOpenTabActionPlace, focus: Boolean) {
    val tab = if (mrIid == null) GitLabReviewTab.NewMergeRequest else GitLabReviewTab.ReviewSelected(mrIid)
    showTab(tab, place)
    if (focus) {
      activate()
    }
  }

  @RequiresEdt
  override fun openMergeRequestTimeline(mrIid: String, focus: Boolean) {
    filesController.openTimeline(mrIid, focus)
  }

  @RequiresEdt
  override fun openMergeRequestDiff(mrIid: String, focus: Boolean) {
    filesController.openDiff(mrIid, focus)
  }

  override fun viewMergeRequestList() {
    selectTab(null)
  }

  override fun closeNewMergeRequestDetails() {
    closeTab(GitLabReviewTab.NewMergeRequest)
  }

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
    private fun GitLabReviewTab.toStatistics(): GitLabStatistics.ToolWindowTabType {
      return when (this) {
        GitLabReviewTab.NewMergeRequest -> GitLabStatistics.ToolWindowTabType.CREATION
        is GitLabReviewTab.ReviewSelected -> GitLabStatistics.ToolWindowTabType.DETAILS
      }
    }
  }
}