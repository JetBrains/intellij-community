// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.ui.diff

import com.intellij.collaboration.async.combineState
import com.intellij.collaboration.async.computationStateIn
import com.intellij.collaboration.ui.codereview.diff.CodeReviewDiffRequestProducer
import com.intellij.collaboration.ui.codereview.diff.DiscussionsViewOption
import com.intellij.collaboration.ui.codereview.diff.model.CodeReviewDiffViewModelComputer
import com.intellij.collaboration.ui.codereview.diff.model.CodeReviewDiscussionsViewModel
import com.intellij.collaboration.ui.codereview.diff.model.ComputedDiffViewModel
import com.intellij.collaboration.ui.codereview.diff.model.DiffProducersViewModel
import com.intellij.collaboration.util.ChangesSelection
import com.intellij.collaboration.util.ComputedResult
import com.intellij.collaboration.util.RefComparisonChange
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.openapi.vcs.changes.actions.diff.ChangeDiffRequestProducer
import com.intellij.platform.util.coroutines.childScope
import com.intellij.util.io.await
import git4idea.changes.GitBranchComparisonResult
import git4idea.changes.GitTextFilePatchWithHistory
import git4idea.changes.createVcsChange
import git4idea.changes.getDiffComputer
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestReviewThread
import org.jetbrains.plugins.github.pullrequest.data.GHPRDataContext
import org.jetbrains.plugins.github.pullrequest.data.provider.GHPRChangesDataProvider
import org.jetbrains.plugins.github.pullrequest.data.provider.GHPRDataProvider
import org.jetbrains.plugins.github.pullrequest.data.provider.createThreadsRequestsFlow
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
) : GHPRDiffViewModel {
  private val cs = parentCs.childScope(CoroutineName("GitHub Pull Request Diff View Model"))
  private val reviewDataProvider = dataProvider.reviewData

  override val reviewVm = DelegatingGHPRReviewViewModel(reviewVmHelper)

  private val changesFetchFlow = dataProvider.changesData.fetchedChangesFlow().shareIn(cs, SharingStarted.Lazily, 1)
  private val helper =
    CodeReviewDiffViewModelComputer(changesFetchFlow) { changesBundle, change ->
      val changeDiffProducer = ChangeDiffRequestProducer.create(project, change.createVcsChange(project))
                               ?: error("Could not create diff producer from $change")
      CodeReviewDiffRequestProducer(project, change, changeDiffProducer, changesBundle.patchesByChange[change]?.getDiffComputer())
    }

  private val changeVmsMap = mutableMapOf<RefComparisonChange, StateFlow<UpdateableGHPRDiffChangeViewModel?>>()

  private val threads: StateFlow<ComputedResult<List<GHPullRequestReviewThread>>> =
    reviewDataProvider.createThreadsRequestsFlow().computationStateIn(cs)

  private val _discussionsViewOption: MutableStateFlow<DiscussionsViewOption> = MutableStateFlow(DiscussionsViewOption.UNRESOLVED_ONLY)
  override val discussionsViewOption: StateFlow<DiscussionsViewOption> = _discussionsViewOption.asStateFlow()

  override val isLoadingReviewData: StateFlow<Boolean> = reviewVm.pendingReview.combineState(threads) { reviewResult, threadsResult ->
    reviewResult.isInProgress || threadsResult.isInProgress
  }

  init {
    dataProvider.changesData.loadChanges()
  }

  override fun reloadReview() {
    reviewDataProvider.resetPendingReview()
    reviewDataProvider.resetReviewThreads()
  }

  override val diffVm: StateFlow<ComputedResult<DiffProducersViewModel?>> =
    helper.diffVm.stateIn(cs, SharingStarted.Eagerly, ComputedResult.loading())

  override fun getViewModelFor(change: RefComparisonChange): StateFlow<UpdateableGHPRDiffChangeViewModel?> =
    changeVmsMap.getOrPut(change) {
      channelFlow {
        var vm: Pair<CoroutineScope, UpdateableGHPRDiffChangeViewModel>? = null
        changesFetchFlow.collectLatest { changesRequest ->
          val diffData = try {
            changesRequest.await().patchesByChange[change]
          }
          catch (e: Exception) {
            return@collectLatest
          }
          if (diffData != null) {
            vm = vm?.also {
              it.second.updateDiffData(diffData)
            } ?: run {
              val vmCs = childScope()
              vmCs to vmCs.createChangeVm(change, diffData)
            }
          }
          else {
            vm?.first?.cancel()
            vm = null
          }
          send(vm?.second)
        }
      }.stateIn(cs, SharingStarted.Lazily, null)
    }

  override suspend fun showDiffFor(changes: ChangesSelection) {
    helper.showChanges(changes)
  }

  override fun setDiscussionsViewOption(viewOption: DiscussionsViewOption) {
    _discussionsViewOption.value = viewOption
  }

  private fun CoroutineScope.createChangeVm(change: RefComparisonChange, diffData: GitTextFilePatchWithHistory)
    : UpdateableGHPRDiffChangeViewModel =
    UpdateableGHPRDiffChangeViewModel(project, this, dataContext, dataProvider, change, diffData, discussionsViewOption)
}

private fun GHPRChangesDataProvider.fetchedChangesFlow(): Flow<Deferred<GitBranchComparisonResult>> =
  channelFlow {
    val listenerDisposable = Disposer.newDisposable()
    val listener: () -> Unit = {
      async {
        try {
          //TODO: don't fetch when not necessary
          fetchBaseBranch().await()
          fetchHeadBranch().await()
          loadChanges().await()
        }
        catch (e: ProcessCanceledException) {
          throw CancellationException("Cancelled", e)
        }
      }.let {
        trySend(it)
      }
    }
    addChangesListener(listenerDisposable, listener)
    listener()
    awaitClose {
      Disposer.dispose(listenerDisposable)
    }
  }.flowOn(Dispatchers.Main)
