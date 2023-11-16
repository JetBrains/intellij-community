// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.ui.details.model.impl

import com.intellij.collaboration.async.modelFlow
import com.intellij.collaboration.ui.codereview.details.model.CodeReviewChangesContainer
import com.intellij.collaboration.ui.codereview.details.model.CodeReviewChangesViewModel
import com.intellij.collaboration.ui.codereview.details.model.CodeReviewChangesViewModelDelegate
import com.intellij.collaboration.util.ComputedResult
import com.intellij.collaboration.util.RefComparisonChange
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vcs.actions.VcsContextFactory
import com.intellij.platform.util.coroutines.childScope
import git4idea.changes.GitBranchComparisonResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.future.await
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.github.api.data.GHCommit
import org.jetbrains.plugins.github.pullrequest.data.GHPRDataContext
import org.jetbrains.plugins.github.pullrequest.data.provider.GHPRDataProvider
import org.jetbrains.plugins.github.pullrequest.ui.GHApiLoadingErrorHandler
import org.jetbrains.plugins.github.pullrequest.ui.details.model.GHPRChangeListViewModel
import org.jetbrains.plugins.github.pullrequest.ui.details.model.GHPRChangeListViewModelImpl
import org.jetbrains.plugins.github.pullrequest.ui.toolwindow.GHPRCommitBrowserComponentController

@ApiStatus.Experimental
interface GHPRChangesViewModel : CodeReviewChangesViewModel<GHCommit>, GHPRCommitBrowserComponentController {
  val changeListVm: StateFlow<ComputedResult<GHPRChangeListViewModel>>
  val changesLoadingErrorHandler: GHApiLoadingErrorHandler

  fun selectChange(change: RefComparisonChange)
}

internal class GHPRChangesViewModelImpl(
  parentCs: CoroutineScope,
  private val project: Project,
  private val dataContext: GHPRDataContext,
  private val dataProvider: GHPRDataProvider
) : GHPRChangesViewModel {
  private val cs = parentCs.childScope()

  private val commitsResult: Flow<Result<List<GHCommit>>> = channelFlow {
    val disp = Disposer.newDisposable()
    var prev: Job? = null
    dataProvider.changesData.loadCommitsFromApi(disp) {
      prev?.cancel()
      prev = launch {
        val result = runCatching {
          it.await()
        }
        send(result)
      }
    }
    awaitClose { Disposer.dispose(disp) }
  }

  private val isUpdatingChanges = MutableStateFlow(false)
  private val changesResult: Flow<Result<GitBranchComparisonResult>> = channelFlow {
    val disp = Disposer.newDisposable()
    var prev: Job? = null
    dataProvider.changesData.loadChanges(disp) {
      val isUpdate = prev != null
      prev?.cancel()
      prev = launch {
        isUpdatingChanges.value = isUpdate
        try {
          val result = runCatching {
            it.await()
          }
          send(result)
        }
        finally {
          isUpdatingChanges.value = false
        }
      }
    }
    awaitClose { Disposer.dispose(disp) }
  }

  init {
    // pre-fetch to show diff quicker
    dataProvider.changesData.fetchBaseBranch()
    dataProvider.changesData.fetchHeadBranch()
  }

  override val changesLoadingErrorHandler = GHApiLoadingErrorHandler(project, dataContext.securityService.account) {
    dataProvider.changesData.reloadChanges()
  }

  override val reviewCommits: StateFlow<List<GHCommit>> = commitsResult
    .map { it.getOrNull() ?: listOf() }
    .stateIn(cs, SharingStarted.Eagerly, listOf())

  private val changesContainer: Flow<Result<CodeReviewChangesContainer>> = changesResult.map { changesResult ->
    changesResult.map {
      CodeReviewChangesContainer(it.changes, it.commits.map { it.sha }, it.changesByCommits)
    }
  }

  private val delegate = CodeReviewChangesViewModelDelegate(cs, changesContainer) {
    GHPRChangeListViewModelImpl(this, project, dataContext, dataProvider, it).also { vm ->
      launch {
        isUpdatingChanges.collect {
          vm.setUpdating(it)
        }
      }
    }
  }

  override val selectedCommitIndex: SharedFlow<Int> = reviewCommits.combine(delegate.selectedCommit) { commits, sha ->
    if (sha == null) -1
    else commits.indexOfFirst { it.oid.startsWith(sha) }
  }.modelFlow(cs, thisLogger())

  override val selectedCommit: SharedFlow<GHCommit?> = reviewCommits.combine(selectedCommitIndex) { commits, index ->
    index.takeIf { it >= 0 }?.let { commits[it] }
  }.modelFlow(cs, thisLogger())

  override val changeListVm: StateFlow<ComputedResult<GHPRChangeListViewModelImpl>> = delegate.changeListVm

  override fun selectCommit(index: Int) {
    delegate.selectCommit(index)
  }

  override fun selectNextCommit() {
    delegate.selectNextCommit()
  }

  override fun selectPreviousCommit() {
    delegate.selectPreviousCommit()
  }

  override fun selectCommit(oid: String) {
    delegate.selectCommit(oid)
  }

  override fun selectChange(oid: String?, filePath: String) {
    val repo = dataContext.repositoryDataService.remoteCoordinates.repository
    val path = VcsContextFactory.getInstance().createFilePath(repo.root, filePath, false)
    delegate.selectChange(oid, path)
  }

  override fun selectChange(change: RefComparisonChange) {
    delegate.selectChange(change)
  }

  override fun commitHash(commit: GHCommit): String = commit.abbreviatedOid
}