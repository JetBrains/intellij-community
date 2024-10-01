// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.ui.toolwindow.model

import com.github.benmanes.caffeine.cache.Caffeine
import com.intellij.collaboration.async.withInitial
import com.intellij.collaboration.ui.toolwindow.ReviewToolwindowProjectViewModel
import com.intellij.collaboration.ui.toolwindow.ReviewToolwindowTabs
import com.intellij.collaboration.ui.toolwindow.ReviewToolwindowTabsStateHolder
import com.intellij.collaboration.util.ComputedResult
import com.intellij.collaboration.util.computeEmitting
import com.intellij.collaboration.util.onFailure
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.platform.util.coroutines.childScope
import com.intellij.util.concurrency.SynchronizedClearableLazy
import git4idea.GitStandardRemoteBranch
import git4idea.push.GitPushRepoResult
import git4idea.remote.hosting.findHostedRemoteBranchTrackedByCurrent
import git4idea.remote.hosting.knownRepositories
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.github.ai.GHPRAIReviewViewModel
import org.jetbrains.plugins.github.api.GHRepositoryConnection
import org.jetbrains.plugins.github.api.GHRepositoryCoordinates
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestShort
import org.jetbrains.plugins.github.pullrequest.GHPRListViewModel
import org.jetbrains.plugins.github.pullrequest.config.GithubPullRequestsProjectUISettings
import org.jetbrains.plugins.github.pullrequest.data.GHPRDataContext
import org.jetbrains.plugins.github.pullrequest.data.GHPRIdentifier
import org.jetbrains.plugins.github.pullrequest.ui.GHPRViewModelContainer
import org.jetbrains.plugins.github.pullrequest.ui.GHPRViewModelContainerImpl
import org.jetbrains.plugins.github.pullrequest.ui.diff.GHPRDiffViewModel
import org.jetbrains.plugins.github.pullrequest.ui.editor.GHPRReviewInEditorViewModel
import org.jetbrains.plugins.github.pullrequest.ui.review.GHPRBranchWidgetViewModel
import org.jetbrains.plugins.github.pullrequest.ui.timeline.GHPRTimelineViewModel
import org.jetbrains.plugins.github.pullrequest.ui.toolwindow.GHPRToolWindowTab
import org.jetbrains.plugins.github.pullrequest.ui.toolwindow.create.GHPRCreateViewModel
import org.jetbrains.plugins.github.pullrequest.ui.toolwindow.create.GHPRCreateViewModelImpl
import org.jetbrains.plugins.github.ui.util.GHUIUtil
import org.jetbrains.plugins.github.util.DisposalCountingHolder
import org.jetbrains.plugins.github.util.GHGitRepositoryMapping
import org.jetbrains.plugins.github.util.GHHostedRepositoriesManager

private val LOG = logger<GHPRToolWindowProjectViewModel>()

@ApiStatus.Experimental
class GHPRToolWindowProjectViewModel internal constructor(
  private val project: Project,
  parentCs: CoroutineScope,
  private val twVm: GHPRToolWindowViewModel,
  connection: GHRepositoryConnection
) : ReviewToolwindowProjectViewModel<GHPRToolWindowTab, GHPRToolWindowTabViewModel> {
  private val cs = parentCs.childScope(javaClass.name)

  internal val dataContext: GHPRDataContext = connection.dataContext

  private val repoManager = project.service<GHHostedRepositoriesManager>()
  private val allRepos = repoManager.knownRepositories.map(GHGitRepositoryMapping::repository)
  val repository: GHRepositoryCoordinates = dataContext.repositoryDataService.repositoryCoordinates
  override val projectName: String = GHUIUtil.getRepositoryDisplayName(allRepos, repository)

  override val listVm: GHPRListViewModel = GHPRListViewModel(project, cs, connection.dataContext)

  private val lazyCreateVm = SynchronizedClearableLazy {
    GHPRCreateViewModelImpl(project, cs, repoManager, GithubPullRequestsProjectUISettings.getInstance(project), connection.dataContext, this)
  }
  internal fun getCreateVmOrNull(): GHPRCreateViewModel? = lazyCreateVm.valueIfInitialized

  private val pullRequestsVms = Caffeine.newBuilder().build<GHPRIdentifier, DisposalCountingHolder<GHPRViewModelContainer>> { id ->
    DisposalCountingHolder {
      GHPRViewModelContainerImpl(project, cs, dataContext, this, id, it)
    }
  }

  private val tabsHelper = ReviewToolwindowTabsStateHolder<GHPRToolWindowTab, GHPRToolWindowTabViewModel>()
  override val tabs: StateFlow<ReviewToolwindowTabs<GHPRToolWindowTab, GHPRToolWindowTabViewModel>> = tabsHelper.tabs.asStateFlow()

  private fun createVm(tab: GHPRToolWindowTab.PullRequest): GHPRToolWindowTabViewModel.PullRequest =
    GHPRToolWindowTabViewModel.PullRequest(cs, this, tab.prId)

  override fun selectTab(tab: GHPRToolWindowTab?) = tabsHelper.select(tab)
  override fun closeTab(tab: GHPRToolWindowTab) = tabsHelper.close(tab)

  fun closeTab(tab: GHPRToolWindowTab.NewPullRequest, reset: Boolean = true) {
    synchronized(lazyCreateVm) {
      tabsHelper.close(tab)
      if (reset) {
        lazyCreateVm.drop()?.let(Disposer::dispose)
        cs.launch {
          dataContext.filesManager.closeNewPrFile()
        }
      }
    }
  }

  fun createPullRequest(requestFocus: Boolean = true) {
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

  fun viewList(requestFocus: Boolean = true) {
    selectTab(null)
    if (requestFocus) {
      listVm.requestFocus()
    }
  }

  fun viewPullRequest(id: GHPRIdentifier, requestFocus: Boolean = true) {
    if (requestFocus) {
      twVm.activate()
    }
    tabsHelper.showTab(GHPRToolWindowTab.PullRequest(id), ::createVm) {
      if (requestFocus) {
        requestFocus()
      }
    }
  }

  fun viewPullRequest(id: GHPRIdentifier, commitOid: String) {
    twVm.activate()
    tabsHelper.showTab(GHPRToolWindowTab.PullRequest(id), ::createVm) {
      selectCommit(commitOid)
    }
  }

  fun openPullRequestInfoAndTimeline(number: Long) {
    cs.launch {
      val prId = dataContext.detailsService.findPRId(number) ?: return@launch // It's an issue ID or doesn't exist

      viewPullRequest(prId, true)
      openPullRequestTimeline(prId, true)
    }
  }

  fun openPullRequestTimeline(id: GHPRIdentifier, requestFocus: Boolean) {
    cs.launch(Dispatchers.Main) {
      dataContext.filesManager.createAndOpenTimelineFile(id, requestFocus)
    }
  }

  fun openPullRequestDiff(id: GHPRIdentifier, requestFocus: Boolean) {
    cs.launch(Dispatchers.Main) {
      dataContext.filesManager.createAndOpenDiffFile(id, requestFocus)
    }
  }

  fun acquireAIReviewViewModel(id: GHPRIdentifier, disposable: Disposable): StateFlow<GHPRAIReviewViewModel?> =
    pullRequestsVms[id].acquireValue(disposable).aiReviewVm

  fun acquireInfoViewModel(id: GHPRIdentifier, disposable: Disposable): GHPRInfoViewModel =
    pullRequestsVms[id].acquireValue(disposable).infoVm

  fun acquireEditorReviewViewModel(id: GHPRIdentifier, disposable: Disposable): GHPRReviewInEditorViewModel =
    pullRequestsVms[id].acquireValue(disposable).editorVm

  fun acquireBranchWidgetModel(id: GHPRIdentifier, disposable: Disposable): GHPRBranchWidgetViewModel =
    pullRequestsVms[id].acquireValue(disposable).branchWidgetVm

  fun acquireDiffViewModel(id: GHPRIdentifier, disposable: Disposable): GHPRDiffViewModel =
    pullRequestsVms[id].acquireValue(disposable).diffVm

  fun acquireTimelineViewModel(id: GHPRIdentifier, disposable: Disposable): GHPRTimelineViewModel =
    pullRequestsVms[id].acquireValue(disposable).timelineVm

  fun findDetails(pullRequest: GHPRIdentifier): GHPullRequestShort? =
    dataContext.listLoader.loadedData.find { it.id == pullRequest.id }
    ?: dataContext.dataProviderRepository.findDataProvider(pullRequest)?.detailsData?.loadedDetails

  private val prOnCurrentBranchRefreshSignal =
    MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

  val prOnCurrentBranch: StateFlow<ComputedResult<GHPRIdentifier?>?> =
    repoManager.findHostedRemoteBranchTrackedByCurrent(connection.repo.gitRepository)
      .combineTransform(prOnCurrentBranchRefreshSignal.withInitial(Unit)) { projectAndBranch, _ ->
        if (projectAndBranch == null) {
          emit(ComputedResult.success(null))
        }
        else {
          val (targetProject, remoteBranch) = projectAndBranch
          computeEmitting {
            val targetRepository = targetProject.repository.repositoryPath
            dataContext.creationService.findOpenPullRequest(null, targetRepository, remoteBranch)
          }?.onFailure {
            LOG.warn("Could not lookup a pull request for current branch", it)
          }
        }
      }.stateIn(cs, SharingStarted.Lazily, null)

  suspend fun isExistingPullRequest(pushResult: GitPushRepoResult): Boolean? {
    val creationService = dataContext.creationService
    val repositoryDataService = dataContext.repositoryDataService

    val repositoryMapping = repositoryDataService.repositoryMapping
    val defaultRemoteBranch = repositoryDataService.getDefaultRemoteBranch() ?: return null

    val pullRequest = creationService.findOpenPullRequest(
      baseBranch = defaultRemoteBranch,
      repositoryMapping.repository.repositoryPath,
      headBranch = GitStandardRemoteBranch(repositoryMapping.gitRemote, pushResult.sourceBranch)
    )

    return pullRequest != null
  }

  fun refreshPrOnCurrentBranch() {
    prOnCurrentBranchRefreshSignal.tryEmit(Unit)
  }
}