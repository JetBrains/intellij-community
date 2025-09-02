// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.ui.toolwindow.model

import com.intellij.collaboration.ui.toolwindow.ReviewToolwindowProjectViewModel
import com.intellij.collaboration.ui.toolwindow.ReviewToolwindowTabs
import com.intellij.collaboration.ui.toolwindow.ReviewToolwindowTabsStateHolder
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.util.concurrency.SynchronizedClearableLazy
import git4idea.remote.hosting.knownRepositories
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.github.api.GHRepositoryConnection
import org.jetbrains.plugins.github.pullrequest.config.GithubPullRequestsProjectUISettings
import org.jetbrains.plugins.github.pullrequest.data.GHPRFilesManagerImpl
import org.jetbrains.plugins.github.pullrequest.data.GHPRIdentifier
import org.jetbrains.plugins.github.pullrequest.ui.GHPRConnectedProjectViewModel
import org.jetbrains.plugins.github.pullrequest.ui.GHPRConnectedProjectViewModelBase
import org.jetbrains.plugins.github.pullrequest.ui.GHPRConnectedProjectViewModelFactory
import org.jetbrains.plugins.github.pullrequest.ui.toolwindow.GHPRToolWindowTab
import org.jetbrains.plugins.github.pullrequest.ui.toolwindow.create.GHPRCreateViewModel
import org.jetbrains.plugins.github.pullrequest.ui.toolwindow.create.GHPRCreateViewModelImpl
import org.jetbrains.plugins.github.ui.util.GHUIUtil
import org.jetbrains.plugins.github.util.GHGitRepositoryMapping
import org.jetbrains.plugins.github.util.GHHostedRepositoriesManager

@ApiStatus.Experimental
class GHPRToolWindowProjectViewModel internal constructor(
  project: Project,
  parentCs: CoroutineScope,
  connection: GHRepositoryConnection,
  private val activate: () -> Unit,
) : ReviewToolwindowProjectViewModel<GHPRToolWindowTab, GHPRToolWindowTabViewModel>, GHPRConnectedProjectViewModelBase(project, parentCs, connection) {

  private val repoManager = project.service<GHHostedRepositoriesManager>()
  private val allRepos = repoManager.knownRepositories.map(GHGitRepositoryMapping::repository)
  private val filesManager = GHPRFilesManagerImpl(project, repository)
  override val projectName: String = GHUIUtil.getRepositoryDisplayName(allRepos, repository)

  private val lazyCreateVm = SynchronizedClearableLazy {
    GHPRCreateViewModelImpl(project, cs, repoManager, GithubPullRequestsProjectUISettings.getInstance(project), connection.dataContext, ::viewPullRequest, ::closeNewPullRequest, ::openPullRequestDiff, ::refreshPrOnCurrentBranch)
  }

  override fun getCreateVmOrNull(): GHPRCreateViewModel? = lazyCreateVm.valueIfInitialized

  init {
    dataContext.dataProviderRepository.addDetailsLoadedListener(cs) {
      filesManager.updateTimelineFilePresentation(it.prId)
    }

    cs.launch {
      try {
        awaitCancellation()
      }
      finally {
        withContext(NonCancellable + ModalityState.any().asContextElement()) {
          filesManager.closeAllFiles()
        }
      }
    }
  }

  private val tabsHelper = ReviewToolwindowTabsStateHolder<GHPRToolWindowTab, GHPRToolWindowTabViewModel>()
  override val tabs: StateFlow<ReviewToolwindowTabs<GHPRToolWindowTab, GHPRToolWindowTabViewModel>> = tabsHelper.tabs.asStateFlow()

  private fun createVm(tab: GHPRToolWindowTab.PullRequest): GHPRToolWindowTabViewModel.PullRequest =
    GHPRToolWindowTabViewModel.PullRequest(cs, this, tab.prId)

  override fun selectTab(tab: GHPRToolWindowTab?) = tabsHelper.select(tab)
  override fun closeTab(tab: GHPRToolWindowTab) = tabsHelper.close(tab)

  override fun closeNewPullRequest() {
    synchronized(lazyCreateVm) {
      tabsHelper.close(GHPRToolWindowTab.NewPullRequest)
      lazyCreateVm.drop()?.let(Disposer::dispose)
      cs.launch {
        filesManager.closeNewPrFile()
      }
    }
  }

  override fun createPullRequest(requestFocus: Boolean) {
    synchronized(lazyCreateVm) {
      tabsHelper.showTab(GHPRToolWindowTab.NewPullRequest, {
        GHPRToolWindowTabViewModel.NewPullRequest(lazyCreateVm.value)
      }) {
        if (requestFocus) {
          requestFocus()
        }
      }
    }
  }

  override fun viewList(requestFocus: Boolean) {
    selectTab(null)
    if (requestFocus) {
      listVm.requestFocus()
    }
  }

  override fun viewPullRequest(id: GHPRIdentifier, requestFocus: Boolean) {
    if (requestFocus) {
      activate()
    }
    tabsHelper.showTab(GHPRToolWindowTab.PullRequest(id), ::createVm) {
      if (requestFocus) {
        requestFocus()
      }
    }
  }

  override fun viewPullRequest(id: GHPRIdentifier, commitOid: String) {
    activate()
    tabsHelper.showTab(GHPRToolWindowTab.PullRequest(id), ::createVm) {
      selectCommit(commitOid)
    }
  }

  override fun openPullRequestTimeline(id: GHPRIdentifier, requestFocus: Boolean) {
    cs.launch(Dispatchers.EDT) {
      filesManager.createAndOpenTimelineFile(id, requestFocus)
    }
  }

  override fun openPullRequestDiff(id: GHPRIdentifier?, requestFocus: Boolean) {
    cs.launch(Dispatchers.EDT) {
      filesManager.createAndOpenDiffFile(id, requestFocus)
    }
  }
}

internal class GHPRToolWindowProjectViewModelFactory: GHPRConnectedProjectViewModelFactory {
  override fun create(project: Project, cs: CoroutineScope, connection: GHRepositoryConnection, activateProject: () -> Unit): GHPRConnectedProjectViewModel {
    return GHPRToolWindowProjectViewModel(project, cs, connection, activateProject)
  }
}