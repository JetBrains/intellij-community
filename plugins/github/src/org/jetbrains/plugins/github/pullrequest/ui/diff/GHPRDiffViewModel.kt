// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.ui.diff

import com.intellij.collaboration.async.*
import com.intellij.collaboration.ui.codereview.diff.CodeReviewDiffRequestProducer
import com.intellij.collaboration.ui.codereview.diff.DiscussionsViewOption
import com.intellij.collaboration.ui.codereview.diff.model.*
import com.intellij.collaboration.ui.codereview.diff.viewer.buildChangeContext
import com.intellij.collaboration.util.ChangesSelection
import com.intellij.collaboration.util.ComputedResult
import com.intellij.collaboration.util.RefComparisonChange
import com.intellij.collaboration.util.getOrNull
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.vcs.changes.actions.diff.ChangeDiffRequestProducer
import com.intellij.platform.util.coroutines.childScope
import git4idea.changes.GitBranchComparisonResult
import git4idea.changes.GitTextFilePatchWithHistory
import git4idea.changes.createVcsChange
import git4idea.changes.getDiffComputer
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestReviewThread
import org.jetbrains.plugins.github.pullrequest.config.GithubPullRequestsProjectUISettings
import org.jetbrains.plugins.github.pullrequest.data.GHPRDataContext
import org.jetbrains.plugins.github.pullrequest.data.provider.GHPRDataProvider
import org.jetbrains.plugins.github.pullrequest.data.provider.threadsComputationFlow
import org.jetbrains.plugins.github.pullrequest.ui.comment.GHPRThreadsViewModels
import org.jetbrains.plugins.github.pullrequest.ui.review.DelegatingGHPRReviewViewModel
import org.jetbrains.plugins.github.pullrequest.ui.review.GHPRReviewViewModel
import org.jetbrains.plugins.github.pullrequest.ui.review.GHPRReviewViewModelHelper

interface GHPRDiffViewModel : ComputedDiffViewModel, CodeReviewDiscussionsViewModel {
  val reviewVm: GHPRReviewViewModel
  val isLoadingReviewData: StateFlow<Boolean>

  fun getViewModelFor(change: RefComparisonChange): Flow<GHPRDiffChangeViewModel?>

  fun reloadReview()

  suspend fun showDiffFor(changes: ChangesSelection)

  companion object {
    val KEY: Key<GHPRDiffViewModel> = Key.create("GitHub.PullRequest.Diff.ViewModel")
    val DATA_KEY: DataKey<GHPRDiffViewModel> = DataKey.create(KEY.toString())
  }
}

internal class GHPRDiffViewModelImpl(
  private val project: Project,
  parentCs: CoroutineScope,
  private val dataContext: GHPRDataContext,
  private val dataProvider: GHPRDataProvider,
  reviewVmHelper: GHPRReviewViewModelHelper,
  private val threadsVms: GHPRThreadsViewModels,
) : GHPRDiffViewModel {
  private val cs = parentCs.childScope(CoroutineName("GitHub Pull Request Diff View Model"))
  private val reviewDataProvider = dataProvider.reviewData

  override val reviewVm = DelegatingGHPRReviewViewModel(reviewVmHelper)

  private val changesFetchFlow = with(dataProvider.changesData) {
    changesNeedReloadSignal.withInitial(Unit).mapScoped(true) {
      async {
        loadChanges().also {
          ensureAllRevisionsFetched()
        }
      }
    }
  }.shareIn(cs, SharingStarted.Lazily, 1)

  private val changesSorter = GithubPullRequestsProjectUISettings.getInstance(project).changesGroupingState
    .map { RefComparisonChangesSorter.Grouping(project, it) }
  private val helper =
    CodeReviewDiffViewModelComputer(changesFetchFlow, changesSorter) { changesBundle, change ->
      val changeContext: Map<Key<*>, Any> = change.buildChangeContext()
      val changeDiffProducer = ChangeDiffRequestProducer.create(project, change.createVcsChange(project), changeContext)
                               ?: error("Could not create diff producer from $change")
      CodeReviewDiffRequestProducer(project, change, changeDiffProducer, changesBundle.patchesByChange[change]?.getDiffComputer())
    }

  private val changeVmsMap = mutableMapOf<RefComparisonChange, StateFlow<GHPRDiffChangeViewModelImpl?>>()

  private val threads: StateFlow<ComputedResult<List<GHPullRequestReviewThread>>> =
    reviewDataProvider.threadsComputationFlow.stateInNow(cs, ComputedResult.loading())

  private val _discussionsViewOption: MutableStateFlow<DiscussionsViewOption> = MutableStateFlow(DiscussionsViewOption.UNRESOLVED_ONLY)
  override val discussionsViewOption: StateFlow<DiscussionsViewOption> = _discussionsViewOption.asStateFlow()

  override val isLoadingReviewData: StateFlow<Boolean> = reviewVm.pendingReview.combineState(threads) { reviewResult, threadsResult ->
    reviewResult.isInProgress || threadsResult.isInProgress
  }

  override fun reloadReview() {
    cs.launch {
      reviewDataProvider.signalPendingReviewNeedsReload()
      reviewDataProvider.signalThreadsNeedReload()
    }
  }

  override val diffVm: StateFlow<ComputedResult<DiffProducersViewModel?>> =
    helper.diffVm.stateIn(cs, SharingStarted.Eagerly, ComputedResult.loading())

  @OptIn(ExperimentalCoroutinesApi::class)
  override fun getViewModelFor(change: RefComparisonChange): StateFlow<GHPRDiffChangeViewModelImpl?> =
    changeVmsMap.getOrPut(change) {
      changesFetchFlow.computationState().transformLatest {
        val result = it.getOrNull<GitBranchComparisonResult>() ?: return@transformLatest
        this.emit(result.patchesByChange[change])
      }.mapNullableScoped { createChangeVm(change, it) }.stateIn(cs, SharingStarted.Lazily, null)
    }

  override suspend fun showDiffFor(changes: ChangesSelection) {
    helper.showChanges(changes)
  }

  override fun setDiscussionsViewOption(viewOption: DiscussionsViewOption) {
    _discussionsViewOption.value = viewOption
  }

  private fun CoroutineScope.createChangeVm(change: RefComparisonChange, diffData: GitTextFilePatchWithHistory) =
    GHPRDiffChangeViewModelImpl(this, dataContext, dataProvider, change, diffData, threadsVms, discussionsViewOption)
}
