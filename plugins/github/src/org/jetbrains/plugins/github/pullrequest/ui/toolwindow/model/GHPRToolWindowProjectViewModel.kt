// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.ui.toolwindow.model

import com.intellij.collaboration.ui.toolwindow.ReviewToolwindowProjectViewModel
import com.intellij.collaboration.ui.toolwindow.ReviewToolwindowTabs
import com.intellij.collaboration.ui.toolwindow.ReviewToolwindowTabsStateHolder
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.platform.util.coroutines.childScope
import com.intellij.util.io.await
import git4idea.GitStandardRemoteBranch
import git4idea.push.GitPushRepoResult
import git4idea.remote.hosting.knownRepositories
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.github.api.GHRepositoryConnection
import org.jetbrains.plugins.github.api.GHRepositoryCoordinates
import org.jetbrains.plugins.github.pullrequest.GHPRListViewModel
import org.jetbrains.plugins.github.pullrequest.data.GHPRDataContext
import org.jetbrains.plugins.github.pullrequest.data.GHPRIdentifier
import org.jetbrains.plugins.github.pullrequest.ui.toolwindow.GHPRToolWindowTab
import org.jetbrains.plugins.github.ui.util.GHUIUtil
import org.jetbrains.plugins.github.util.GHGitRepositoryMapping
import org.jetbrains.plugins.github.util.GHHostedRepositoriesManager

@ApiStatus.Experimental
class GHPRToolWindowProjectViewModel internal constructor(
  private val project: Project,
  parentCs: CoroutineScope,
  connection: GHRepositoryConnection
) : ReviewToolwindowProjectViewModel<GHPRToolWindowTab, GHPRToolWindowTabViewModel> {
  private val cs = parentCs.childScope()

  internal val dataContext: GHPRDataContext = connection.dataContext
  val defaultBranch: String? = dataContext.repositoryDataService.defaultBranchName

  private val allRepos = project.service<GHHostedRepositoriesManager>().knownRepositories.map(GHGitRepositoryMapping::repository)
  val repository: GHRepositoryCoordinates = dataContext.repositoryDataService.repositoryCoordinates
  override val projectName: String = GHUIUtil.getRepositoryDisplayName(allRepos, repository)

  override val listVm: GHPRListViewModel = GHPRListViewModel(project, cs, connection.dataContext)

  private val tabsHelper = ReviewToolwindowTabsStateHolder<GHPRToolWindowTab, GHPRToolWindowTabViewModel>()
  override val tabs: StateFlow<ReviewToolwindowTabs<GHPRToolWindowTab, GHPRToolWindowTabViewModel>> = tabsHelper.tabs.asStateFlow()

  private fun createVm(tab: GHPRToolWindowTab.PullRequest): GHPRToolWindowTabViewModel.PullRequest =
    GHPRToolWindowTabViewModel.PullRequest(project, cs, dataContext, tab.prId)

  private fun createVm(tab: GHPRToolWindowTab.NewPullRequest): GHPRToolWindowTabViewModel.NewPullRequest =
    GHPRToolWindowTabViewModel.NewPullRequest(project, dataContext)

  override fun selectTab(tab: GHPRToolWindowTab?) = tabsHelper.select(tab)
  override fun closeTab(tab: GHPRToolWindowTab) = tabsHelper.close(tab)

  fun createPullRequest(requestFocus: Boolean = true) {
    tabsHelper.showTab(GHPRToolWindowTab.NewPullRequest, ::createVm) {
      if (requestFocus) {
        requestFocus()
      }
    }
  }

  fun viewList(requestFocus: Boolean = true) {
    selectTab(null)
    if (requestFocus) {
      listVm.requestFocus()
    }
  }

  fun viewPullRequest(id: GHPRIdentifier, requestFocus: Boolean = true) {
    tabsHelper.showTab(GHPRToolWindowTab.PullRequest(id), ::createVm) {
      if (requestFocus) {
        requestFocus()
      }
    }
  }

  fun viewPullRequest(id: GHPRIdentifier, commitOid: String) {
    tabsHelper.showTab(GHPRToolWindowTab.PullRequest(id), ::createVm) {
      selectCommit(commitOid)
    }
  }

  fun viewPullRequest(id: GHPRIdentifier, commitOid: String?, filePath: String) {
    tabsHelper.showTab(GHPRToolWindowTab.PullRequest(id), ::createVm) {
      selectChange(commitOid, filePath)
    }
  }

  fun openPullRequestTimeline(id: GHPRIdentifier, requestFocus: Boolean) =
    dataContext.filesManager.createAndOpenTimelineFile(id, requestFocus)

  fun openPullRequestDiff(id: GHPRIdentifier, requestFocus: Boolean) =
    dataContext.filesManager.createAndOpenDiffFile(id, requestFocus)

  suspend fun isExistingPullRequest(pushResult: GitPushRepoResult): Boolean? {
    val creationService = dataContext.creationService
    val repositoryDataService = dataContext.repositoryDataService

    val repositoryMapping = repositoryDataService.repositoryMapping
    val defaultRemoteBranch = repositoryDataService.getDefaultRemoteBranch() ?: return null

    val pullRequest = creationService.findPullRequestAsync(
      EmptyProgressIndicator(),
      baseBranch = defaultRemoteBranch,
      repositoryMapping,
      headBranch = GitStandardRemoteBranch(repositoryMapping.gitRemote, pushResult.sourceBranch)
    ).await()

    return pullRequest != null
  }
}