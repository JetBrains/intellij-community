// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.ui

import com.github.benmanes.caffeine.cache.Caffeine
import com.intellij.collaboration.async.withInitial
import com.intellij.collaboration.util.ComputedResult
import com.intellij.collaboration.util.computeEmitting
import com.intellij.collaboration.util.onFailure
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.platform.util.coroutines.childScope
import git4idea.remote.hosting.findHostedRemoteBranchTrackedByCurrent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.github.ai.GHPRAIReviewViewModel
import org.jetbrains.plugins.github.ai.GHPRAISummaryViewModel
import org.jetbrains.plugins.github.api.GHRepositoryConnection
import org.jetbrains.plugins.github.api.GHRepositoryCoordinates
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestShort
import org.jetbrains.plugins.github.pullrequest.GHPRListViewModel
import org.jetbrains.plugins.github.pullrequest.data.GHPRDataContext
import org.jetbrains.plugins.github.pullrequest.data.GHPRIdentifier
import org.jetbrains.plugins.github.pullrequest.ui.diff.GHPRDiffViewModel
import org.jetbrains.plugins.github.pullrequest.ui.editor.GHPRReviewInEditorViewModel
import org.jetbrains.plugins.github.pullrequest.ui.review.GHPRBranchWidgetViewModel
import org.jetbrains.plugins.github.pullrequest.ui.timeline.GHPRTimelineViewModel
import org.jetbrains.plugins.github.pullrequest.ui.toolwindow.create.GHPRCreateViewModel
import org.jetbrains.plugins.github.pullrequest.ui.toolwindow.model.GHPRInfoViewModel
import org.jetbrains.plugins.github.util.DisposalCountingHolder
import org.jetbrains.plugins.github.util.GHHostedRepositoriesManager

@ApiStatus.Internal
interface GHPRConnectedProjectViewModel {
  val repository: GHRepositoryCoordinates
  val dataContext: GHPRDataContext
  val listVm: GHPRListViewModel
  val prOnCurrentBranch: StateFlow<ComputedResult<GHPRIdentifier?>?>

  fun getCreateVmOrNull(): GHPRCreateViewModel?

  fun acquireAIReviewViewModel(id: GHPRIdentifier, disposable: Disposable): StateFlow<GHPRAIReviewViewModel?>
  fun acquireAISummaryViewModel(id: GHPRIdentifier, disposable: Disposable): StateFlow<GHPRAISummaryViewModel?>
  fun acquireInfoViewModel(id: GHPRIdentifier, disposable: Disposable): GHPRInfoViewModel
  fun acquireEditorReviewViewModel(id: GHPRIdentifier, disposable: Disposable): GHPRReviewInEditorViewModel
  fun acquireBranchWidgetModel(id: GHPRIdentifier, disposable: Disposable): GHPRBranchWidgetViewModel
  fun acquireDiffViewModel(id: GHPRIdentifier, disposable: Disposable): GHPRDiffViewModel
  fun acquireTimelineViewModel(id: GHPRIdentifier, disposable: Disposable): GHPRTimelineViewModel

  fun findDetails(id: GHPRIdentifier): GHPullRequestShort?

  fun createPullRequest(requestFocus: Boolean = true)
  fun viewList(requestFocus: Boolean = true)
  fun viewPullRequest(id: GHPRIdentifier, requestFocus: Boolean = true)
  fun openPullRequestInfoAndTimeline(number: Long)
  fun openPullRequestTimeline(id: GHPRIdentifier, requestFocus: Boolean)
  fun openPullRequestDiff(id: GHPRIdentifier?, requestFocus: Boolean)
}

private val LOG = logger<GHPRConnectedProjectViewModelBase>()

@ApiStatus.Internal
abstract class GHPRConnectedProjectViewModelBase(
  private val project: Project,
  parentCs: CoroutineScope,
  connection: GHRepositoryConnection,
) : GHPRConnectedProjectViewModel {
  protected val cs: CoroutineScope = parentCs.childScope(javaClass.name)

  override val dataContext: GHPRDataContext = connection.dataContext
  override val repository: GHRepositoryCoordinates = dataContext.repositoryDataService.repositoryCoordinates
  override val listVm: GHPRListViewModel = GHPRListViewModel(project, cs, connection.dataContext)

  private val repoManager: GHHostedRepositoriesManager = project.service<GHHostedRepositoriesManager>()
  private val pullRequestsVms = Caffeine.newBuilder().build<GHPRIdentifier, DisposalCountingHolder<GHPRViewModelContainer>> { id ->
    DisposalCountingHolder {
      GHPRViewModelContainerImpl(project, cs, dataContext, id, it, ::viewPullRequest, ::viewPullRequest, ::openPullRequestDiff,
                                 ::refreshPrOnCurrentBranch)
    }
  }

  @ApiStatus.Internal
  override fun acquireAIReviewViewModel(id: GHPRIdentifier, disposable: Disposable): StateFlow<GHPRAIReviewViewModel?> =
    pullRequestsVms[id].acquireValue(disposable).aiReviewVm

  override fun acquireAISummaryViewModel(id: GHPRIdentifier, disposable: Disposable): StateFlow<GHPRAISummaryViewModel?> =
    pullRequestsVms[id].acquireValue(disposable).aiSummaryVm

  override fun acquireInfoViewModel(id: GHPRIdentifier, disposable: Disposable): GHPRInfoViewModel =
    pullRequestsVms[id].acquireValue(disposable).infoVm

  override fun acquireEditorReviewViewModel(id: GHPRIdentifier, disposable: Disposable): GHPRReviewInEditorViewModel =
    pullRequestsVms[id].acquireValue(disposable).editorVm

  override fun acquireBranchWidgetModel(id: GHPRIdentifier, disposable: Disposable): GHPRBranchWidgetViewModel =
    pullRequestsVms[id].acquireValue(disposable).branchWidgetVm

  @ApiStatus.Internal
  override fun acquireDiffViewModel(id: GHPRIdentifier, disposable: Disposable): GHPRDiffViewModel =
    pullRequestsVms[id].acquireValue(disposable).diffVm

  override fun acquireTimelineViewModel(id: GHPRIdentifier, disposable: Disposable): GHPRTimelineViewModel =
    pullRequestsVms[id].acquireValue(disposable).timelineVm

  override fun findDetails(id: GHPRIdentifier): GHPullRequestShort? =
    dataContext.listLoader.loadedData.value.find { it.id == id.id }
    ?: dataContext.dataProviderRepository.findDataProvider(id)?.detailsData?.loadedDetails

  private val prOnCurrentBranchRefreshSignal =
    MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

  override val prOnCurrentBranch: StateFlow<ComputedResult<GHPRIdentifier?>?> =
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

  protected fun refreshPrOnCurrentBranch() {
    prOnCurrentBranchRefreshSignal.tryEmit(Unit)
  }

  override fun openPullRequestInfoAndTimeline(number: Long) {
    cs.launch {
      val prId = dataContext.detailsService.findPRId(number) ?: return@launch // It's an issue ID or doesn't exist

      viewPullRequest(prId, true)
      openPullRequestTimeline(prId, true)
    }
  }

  abstract fun viewPullRequest(id: GHPRIdentifier, commitOid: String)
  abstract fun closeNewPullRequest()
}