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
import com.intellij.collaboration.util.*
import com.intellij.diff.requests.DiffRequest
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.openapi.vcs.changes.actions.diff.ChangeDiffRequestProducer
import com.intellij.platform.util.coroutines.childScope
import com.intellij.util.cancelOnDispose
import com.intellij.util.io.await
import git4idea.changes.GitBranchComparisonResult
import git4idea.changes.createVcsChange
import git4idea.changes.getDiffComputer
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestReviewThread
import org.jetbrains.plugins.github.pullrequest.comment.GHPRDiffReviewSupport
import org.jetbrains.plugins.github.pullrequest.comment.GHPRDiffReviewSupportImpl
import org.jetbrains.plugins.github.pullrequest.data.GHPRDataContext
import org.jetbrains.plugins.github.pullrequest.data.GHPullRequestPendingReview
import org.jetbrains.plugins.github.pullrequest.data.provider.GHPRChangesDataProvider
import org.jetbrains.plugins.github.pullrequest.data.provider.GHPRDataProvider
import org.jetbrains.plugins.github.pullrequest.data.provider.createThreadsRequestsFlow
import org.jetbrains.plugins.github.pullrequest.ui.changes.GHPRViewedStateDiffSupport
import org.jetbrains.plugins.github.pullrequest.ui.changes.GHPRViewedStateDiffSupportImpl
import org.jetbrains.plugins.github.pullrequest.ui.review.DelegatingGHPRReviewViewModel
import org.jetbrains.plugins.github.pullrequest.ui.review.GHPRReviewViewModel
import org.jetbrains.plugins.github.pullrequest.ui.review.GHPRReviewViewModelHelper

interface GHPRDiffViewModel : ComputedDiffViewModel, CodeReviewDiscussionsViewModel {
  val reviewVm: GHPRReviewViewModel
  val isLoadingReviewData: StateFlow<Boolean>

  fun reloadReview()

  suspend fun showDiffFor(changes: ChangesSelection)

  companion object {
    val DATA_KEY: DataKey<GHPRDiffViewModel> = DataKey.create("GitHub.PullRequest.Diff.ViewModel")
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

  private val repositoryDataService = dataContext.repositoryDataService
  private val viewedStateData = dataProvider.viewedStateData
  private val reviewDataProvider = dataProvider.reviewData

  private val viewedStateSupport = GHPRViewedStateDiffSupportImpl(repositoryDataService.repositoryMapping.gitRepository,
                                                                  viewedStateData)
  override val reviewVm = DelegatingGHPRReviewViewModel(reviewVmHelper)

  private val helper = CodeReviewDiffViewModelComputer(dataProvider.changesData.fetchedChangesFlow()) { changesBundle, change ->
    val changeDiffProducer = ChangeDiffRequestProducer.create(project, change.createVcsChange(project))
                             ?: error("Could not create diff producer from $change")
    CodeReviewDiffRequestProducer(project, change, changeDiffProducer, changesBundle.patchesByChange[change]?.getDiffComputer()) {
      addData(changesBundle, change)
    }
  }

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

  override suspend fun showDiffFor(changes: ChangesSelection) {
    helper.showChanges(changes)
  }

  override fun setDiscussionsViewOption(viewOption: DiscussionsViewOption) {
    _discussionsViewOption.value = viewOption
  }

  private fun DiffRequest.addData(changesProvider: GitBranchComparisonResult, change: RefComparisonChange): Map<Key<out Any>, Any?> {
    val requestDataKeys = mutableMapOf<Key<out Any>, Any?>()
    val reviewSupport = createReviewSupport(changesProvider, change)
    if (reviewSupport != null) {
      putUserData(GHPRDiffReviewSupport.KEY, reviewSupport)
    }

    if (changesProvider.changes.contains(change)) {
      putUserData(GHPRViewedStateDiffSupport.KEY, viewedStateSupport)
      putUserData(GHPRViewedStateDiffSupport.PULL_REQUEST_FILE, change.filePath)
    }
    return requestDataKeys
  }

  fun loadReviewThreads(disposable: Disposable, consumer: (ComputedResult<List<GHPullRequestReviewThread>>) -> Unit) {
    cs.launch {
      discussionsViewOption.combine(threads) { viewOption, threadsResult ->
        threadsResult.map { list ->
          when (viewOption) {
            DiscussionsViewOption.ALL -> list
            DiscussionsViewOption.UNRESOLVED_ONLY -> list.filter { !it.isResolved }
            DiscussionsViewOption.DONT_SHOW -> emptyList()
          }
        }
      }.collect {
        withContext(Dispatchers.Main) {
          consumer(it)
        }
      }
    }.cancelOnDispose(disposable, false)
  }

  fun loadPendingReview(disposable: Disposable, consumer: (ComputedResult<GHPullRequestPendingReview?>) -> Unit) {
    cs.launch {
      reviewVm.pendingReview.collect {
        withContext(Dispatchers.Main) {
          consumer(it)
        }
      }
    }.cancelOnDispose(disposable, false)
  }

  private fun createReviewSupport(changesProvider: GitBranchComparisonResult, change: RefComparisonChange): GHPRDiffReviewSupport? {
    val diffData = changesProvider.patchesByChange[change] ?: return null

    return GHPRDiffReviewSupportImpl(project,
                                     reviewDataProvider, dataProvider.detailsData,
                                     dataContext.htmlImageLoader,
                                     dataContext.avatarIconsProvider,
                                     repositoryDataService,
                                     this,
                                     diffData,
                                     dataContext.securityService.ghostUser,
                                     dataContext.securityService.currentUser)
  }
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