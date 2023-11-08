// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.ui.details.model

import com.intellij.collaboration.async.CompletableFutureUtil.handleOnEdt
import com.intellij.collaboration.ui.codereview.details.model.CodeReviewChangeDetails
import com.intellij.collaboration.ui.codereview.details.model.CodeReviewChangeList
import com.intellij.collaboration.ui.codereview.details.model.CodeReviewChangeListViewModel
import com.intellij.collaboration.ui.codereview.details.model.CodeReviewChangeListViewModelBase
import com.intellij.collaboration.util.RefComparisonChange
import com.intellij.collaboration.util.filePath
import com.intellij.diff.DiffDialogHints
import com.intellij.diff.DiffManager
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.changes.DiffPreview
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.vcsUtil.VcsFileUtil.relativePath
import git4idea.repo.GitRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestFileViewedState
import org.jetbrains.plugins.github.api.data.pullrequest.isViewed
import org.jetbrains.plugins.github.pullrequest.data.GHPRDataContext
import org.jetbrains.plugins.github.pullrequest.data.provider.GHPRDataProvider
import org.jetbrains.plugins.github.pullrequest.ui.changes.GHPRDiffRequestChainProducer
import org.jetbrains.plugins.github.pullrequest.ui.changes.GHPRViewedStateDiffSupport
import org.jetbrains.plugins.github.pullrequest.ui.changes.GHPRViewedStateDiffSupportImpl
import org.jetbrains.plugins.github.pullrequest.ui.review.DelegatingGHPRReviewViewModel
import org.jetbrains.plugins.github.pullrequest.ui.review.GHPRReviewViewModelHelper

@ApiStatus.Experimental
interface GHPRChangeListViewModel : CodeReviewChangeListViewModel.WithDetails, DiffPreview {
  val isUpdating: StateFlow<Boolean>

  fun canShowDiff(): Boolean
  fun showDiff()

  /**
   * Tests if the viewed state matches for all files
   */
  @RequiresEdt
  fun isViewedStateForAllFiles(files: Iterable<FilePath>, viewed: Boolean): Boolean?

  /**
   * Set a viewed state for all files
   */
  @RequiresEdt
  fun setViewedState(files: Iterable<FilePath>, viewed: Boolean)

  companion object {
    val DATA_KEY = DataKey.create<GHPRChangeListViewModel>("GitHub.PullRequest.Details.Changes.List.ViewModel")
  }
}

internal class GHPRChangeListViewModelImpl(
  parentCs: CoroutineScope,
  override val project: Project,
  private val dataContext: GHPRDataContext,
  private val dataProvider: GHPRDataProvider,
  private val reviewVmHelper: GHPRReviewViewModelHelper,
  changeList: CodeReviewChangeList
) : GHPRChangeListViewModel, CodeReviewChangeListViewModelBase(parentCs, changeList) {
  private val repository: GitRepository get() = dataContext.repositoryDataService.remoteCoordinates.repository

  private val _isUpdating = MutableStateFlow(false)
  override val isUpdating: StateFlow<Boolean> = _isUpdating.asStateFlow()

  private val viewedStateData = dataProvider.viewedStateData
  private val viewedStateSupport = GHPRViewedStateDiffSupportImpl(repository, viewedStateData)
  private val reviewVm = DelegatingGHPRReviewViewModel(reviewVmHelper)
  private val diffRequestProducer: GHPRDiffRequestChainProducer =
    GHPRDiffRequestChainProducer(project,
                                 dataProvider,
                                 dataContext.htmlImageLoader, dataContext.avatarIconsProvider,
                                 dataContext.repositoryDataService,
                                 dataContext.securityService.ghostUser,
                                 dataContext.securityService.currentUser,
                                 reviewVm) {

      if (selectedCommit != null) emptyMap()
      else mapOf(
        GHPRViewedStateDiffSupport.KEY to viewedStateSupport,
        GHPRViewedStateDiffSupport.PULL_REQUEST_FILE to it.filePath
      )
    }

  override val detailsByChange: StateFlow<Map<RefComparisonChange, CodeReviewChangeDetails>> =
    if (changeList.commitSha == null) {
      createDetailsByChangeFlow().stateIn(cs, SharingStarted.Eagerly, emptyMap())
    }
    else {
      MutableStateFlow(emptyMap())
    }

  fun setUpdating(updating: Boolean) {
    _isUpdating.value = updating
  }

  init {
    cs.launch {
      changesSelection.collect { selection ->
        dataProvider.combinedDiffSelectionModel.updateSelectedChanges(selection)
        dataProvider.diffRequestModel.requestChain = selection?.let { diffRequestProducer.getRequestChain(it) }
      }
    }
  }

  override fun showDiffPreview() {
    dataContext.filesManager.createAndOpenDiffFile(dataProvider.id, true)
  }

  override fun openPreview(requestFocus: Boolean): Boolean {
    dataContext.filesManager.createAndOpenDiffFile(dataProvider.id, requestFocus)
    return true
  }

  override fun closePreview() = Unit

  override fun canShowDiff(): Boolean = dataProvider.diffRequestModel.requestChain != null

  override fun showDiff() {
    val requestChain = dataProvider.diffRequestModel.requestChain ?: return
    DiffManager.getInstance().showDiff(project, requestChain, DiffDialogHints.DEFAULT)
  }

  override fun isViewedStateForAllFiles(files: Iterable<FilePath>, viewed: Boolean): Boolean? {
    if (selectedCommit != null) return null
    val state = viewedStateData.getViewedState() ?: return null
    return files.all {
      val relativePath = relativePath(repository.root, it)
      (state[relativePath]?.isViewed() ?: false) == viewed
    }
  }

  override fun setViewedState(files: Iterable<FilePath>, viewed: Boolean) {
    //TODO: check API for batch update
    val state = viewedStateData.getViewedState() ?: return
    files.asSequence().map {
      relativePath(repository.root, it)
    }.filter {
      state[it]?.isViewed() != viewed
    }.forEach {
      viewedStateData.updateViewedState(it, viewed)
    }
  }

  private fun createDetailsByChangeFlow(): Flow<Map<RefComparisonChange, CodeReviewChangeDetails>> = channelFlow {
    var unresolvedThreadsByPath = emptyMap<String, Int>()
    var viewedStateByPath = emptyMap<String, GHPullRequestFileViewedState>()
    val disposable = Disposer.newDisposable()

    fun updateAndSend() {
      val result = changes.associateWith { change ->
        val path = relativePath(repository.root, change.filePath)
        val isRead = viewedStateByPath[path]?.isViewed() ?: true
        val discussions = unresolvedThreadsByPath[path] ?: 0
        CodeReviewChangeDetails(isRead, discussions)
      }
      trySend(result)
    }

    fun loadThreads() {
      dataProvider.reviewData.loadReviewThreads().handleOnEdt(disposable) { threads, _ ->
        threads ?: return@handleOnEdt // error

        unresolvedThreadsByPath = threads.asSequence().filter { !it.isResolved }.groupingBy { it.path }.eachCount()
        updateAndSend()
      }
    }

    fun loadViewedState() {
      viewedStateData.loadViewedState().handleOnEdt(disposable) { viewedState, _ ->
        viewedState ?: return@handleOnEdt // error

        viewedStateByPath = viewedState
        updateAndSend()
      }
    }

    loadViewedState()
    viewedStateData.addViewedStateListener(disposable) { loadViewedState() }

    loadThreads()
    dataProvider.reviewData.addReviewThreadsListener(disposable) { loadThreads() }

    send(emptyMap())

    awaitClose {
      Disposer.dispose(disposable)
    }
  }
}