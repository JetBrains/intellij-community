// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.ui.details.model.impl

import com.intellij.collaboration.async.nestedDisposable
import com.intellij.collaboration.ui.codereview.details.model.CodeReviewChangesViewModelBase
import com.intellij.diff.DiffDialogHints
import com.intellij.diff.DiffManager
import com.intellij.openapi.ListSelection
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ChangesUtil
import com.intellij.util.childScope
import git4idea.changes.GitBranchComparisonResult
import git4idea.repo.GitRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import org.jetbrains.plugins.github.api.data.GHCommit
import org.jetbrains.plugins.github.api.data.GHUser
import org.jetbrains.plugins.github.pullrequest.GHPRStatisticsCollector
import org.jetbrains.plugins.github.pullrequest.data.GHPRDataContext
import org.jetbrains.plugins.github.pullrequest.data.provider.GHPRDataProvider
import org.jetbrains.plugins.github.pullrequest.ui.GHApiLoadingErrorHandler
import org.jetbrains.plugins.github.pullrequest.ui.GHCompletableFutureLoadingModel
import org.jetbrains.plugins.github.pullrequest.ui.changes.GHPRDiffRequestChainProducer
import org.jetbrains.plugins.github.pullrequest.ui.changes.GHPRProgressTreeModel
import org.jetbrains.plugins.github.pullrequest.ui.changes.GHPRViewedStateDiffSupport
import org.jetbrains.plugins.github.pullrequest.ui.changes.GHPRViewedStateDiffSupportImpl
import org.jetbrains.plugins.github.pullrequest.ui.getResultFlow

internal class GHPRChangesViewModel(
  parentCs: CoroutineScope,
  private val project: Project,
  private val dataContext: GHPRDataContext,
  private val dataProvider: GHPRDataProvider
) : CodeReviewChangesViewModelBase<GHCommit>() {
  private val cs = parentCs.childScope()

  private val repository: GitRepository get() = dataContext.repositoryDataService.remoteCoordinates.repository

  private val commitsLoadingModel = GHCompletableFutureLoadingModel<List<GHCommit>>(cs.nestedDisposable())
  val changesLoadingModel = GHCompletableFutureLoadingModel<GitBranchComparisonResult>(cs.nestedDisposable())

  init {
    dataProvider.changesData.loadCommitsFromApi(cs.nestedDisposable()) {
      commitsLoadingModel.future = it
    }
    dataProvider.changesData.loadChanges(cs.nestedDisposable()) {
      changesLoadingModel.future = it
    }
    // pre-fetch to show diff quicker
    dataProvider.changesData.fetchBaseBranch()
    dataProvider.changesData.fetchHeadBranch()
  }

  val changesLoadingErrorHandler = GHApiLoadingErrorHandler(project, dataContext.securityService.account) {
    dataProvider.changesData.reloadChanges()
  }

  override val reviewCommits: StateFlow<List<GHCommit>> = commitsLoadingModel.getResultFlow()
    .map { commits -> commits?.asReversed() ?: listOf() }
    .stateIn(cs, SharingStarted.Eagerly, listOf())

  override fun commitHash(commit: GHCommit): String {
    return commit.abbreviatedOid
  }

  override fun selectCommit(index: Int) {
    super.selectCommit(index)
    GHPRStatisticsCollector.logDetailsCommitChosen(project)
  }

  override fun selectNextCommit() {
    super.selectNextCommit()
    GHPRStatisticsCollector.logDetailsNextCommitChosen(project)
  }

  override fun selectPreviousCommit() {
    super.selectPreviousCommit()
    GHPRStatisticsCollector.logDetailsPrevCommitChosen(project)
  }

  val progressModel: GHPRProgressTreeModel = GHPRProgressTreeModel(repository, dataProvider.reviewData, dataProvider.viewedStateData) {
    _selectedCommitIndexState.value < 0
  }.also {
    Disposer.register(cs.nestedDisposable(), it)
  }

  private val diffRequestProducer: GHPRDiffRequestChainProducer =
    object : GHPRDiffRequestChainProducer(project,
                                          dataProvider,
                                          dataContext.htmlImageLoader, dataContext.avatarIconsProvider,
                                          dataContext.repositoryDataService,
                                          dataContext.securityService.ghostUser,
                                          dataContext.securityService.currentUser) {

      private val viewedStateSupport = GHPRViewedStateDiffSupportImpl(repository, dataProvider.viewedStateData)

      override fun createCustomContext(change: Change): Map<Key<*>, Any> {
        if (_selectedCommitIndexState.value >= 0) return emptyMap()

        return mapOf(
          GHPRViewedStateDiffSupport.KEY to viewedStateSupport,
          GHPRViewedStateDiffSupport.PULL_REQUEST_FILE to ChangesUtil.getFilePath(change)
        )
      }
    }

  val diffFilePathSelectionEvents: SharedFlow<FilePath?> = callbackFlow {
    val listenerDisposable = Disposer.newDisposable()
    dataProvider.diffRequestModel.addFilePathSelectionListener(listenerDisposable) {
      trySend(dataProvider.diffRequestModel.selectedFilePath)
    }
    awaitClose {
      Disposer.dispose(listenerDisposable)
    }
  }.shareIn(cs, SharingStarted.Lazily, 0)

  fun canShowDiff(): Boolean = dataProvider.diffRequestModel.requestChain != null

  fun showDiff() {
    val requestChain = dataProvider.diffRequestModel.requestChain ?: return
    DiffManager.getInstance().showDiff(project, requestChain, DiffDialogHints.DEFAULT)
  }

  fun setSelection(selection: ListSelection<Change>) {
    dataProvider.diffRequestModel.requestChain = selection.let(diffRequestProducer::getRequestChain)
  }

  companion object {
    val DATA_KEY = DataKey.create<GHPRChangesViewModel>("GitHub.PullRequest.Details.Changes.ViewModel")
  }
}