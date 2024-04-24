// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.ui.details.model.impl

import com.intellij.collaboration.async.launchNowIn
import com.intellij.collaboration.async.modelFlow
import com.intellij.collaboration.async.withInitial
import com.intellij.collaboration.ui.codereview.details.model.CodeReviewChangesContainer
import com.intellij.collaboration.ui.codereview.details.model.CodeReviewChangesViewModel
import com.intellij.collaboration.ui.codereview.details.model.CodeReviewChangesViewModelDelegate
import com.intellij.collaboration.util.ComputedResult
import com.intellij.collaboration.util.RefComparisonChange
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.platform.util.coroutines.childScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.github.api.data.GHCommit
import org.jetbrains.plugins.github.pullrequest.data.GHPRDataContext
import org.jetbrains.plugins.github.pullrequest.data.provider.GHPRDataProvider
import org.jetbrains.plugins.github.pullrequest.ui.GHApiLoadingErrorHandler
import org.jetbrains.plugins.github.pullrequest.ui.details.model.GHPRChangeListViewModel
import org.jetbrains.plugins.github.pullrequest.ui.details.model.GHPRChangeListViewModelImpl

@ApiStatus.Experimental
interface GHPRChangesViewModel : CodeReviewChangesViewModel<GHCommit> {
  val changeListVm: StateFlow<ComputedResult<GHPRChangeListViewModel>>
  val changesLoadingErrorHandler: GHApiLoadingErrorHandler

  fun selectCommit(sha: String)
  fun selectChange(change: RefComparisonChange)
}

internal class GHPRChangesViewModelImpl(
  parentCs: CoroutineScope,
  private val project: Project,
  private val dataContext: GHPRDataContext,
  private val dataProvider: GHPRDataProvider
) : GHPRChangesViewModel {
  private val cs = parentCs.childScope()

  private val isLoadingChanges = MutableStateFlow(false)

  override val changesLoadingErrorHandler = GHApiLoadingErrorHandler(project, dataContext.securityService.account) {
    cs.launch {
      dataProvider.changesData.signalChangesNeedReload()
    }
  }

  @OptIn(ExperimentalCoroutinesApi::class)
  override val reviewCommits: StateFlow<List<GHCommit>> =
    dataProvider.changesData.changesNeedReloadSignal.withInitial(Unit).transformLatest {
      try {
        dataProvider.changesData.loadCommits()
      }
      catch (e: Exception) {
        emptyList()
      }.also {
        emit(it)
      }
    }.stateIn(cs, SharingStarted.Eagerly, listOf())

  private val changesContainer: Flow<Result<CodeReviewChangesContainer>> =
    dataProvider.changesData.changesNeedReloadSignal.withInitial(Unit).map {
    isLoadingChanges.value = true
    try {
      runCatching {
        dataProvider.changesData.loadChanges()
      }.map {
        CodeReviewChangesContainer(it.changes, it.commits.map { it.sha }, it.changesByCommits)
      }
    }
    finally {
      isLoadingChanges.value = false
    }
  }.shareIn(cs, SharingStarted.Lazily, 1)

  private val delegate = CodeReviewChangesViewModelDelegate(cs, changesContainer) { changes, changeList ->
    GHPRChangeListViewModelImpl(this, project, dataContext, dataProvider, changes, changeList).also { vm ->
      changesContainer.combine(isLoadingChanges) { _, loading ->
        vm.setUpdating(loading)
      }.launchNowIn(this)
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

  override fun selectCommit(sha: String) {
    delegate.selectCommit(sha)
  }

  override fun selectChange(change: RefComparisonChange) {
    delegate.selectChange(change)
  }

  override fun commitHash(commit: GHCommit): String = commit.abbreviatedOid
}