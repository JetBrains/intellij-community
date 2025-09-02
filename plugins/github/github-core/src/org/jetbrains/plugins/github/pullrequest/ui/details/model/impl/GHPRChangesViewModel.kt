// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.ui.details.model.impl

import com.intellij.collaboration.async.modelFlow
import com.intellij.collaboration.async.stateInNow
import com.intellij.collaboration.async.withInitial
import com.intellij.collaboration.ui.codereview.details.model.CodeReviewChangesContainer
import com.intellij.collaboration.ui.codereview.details.model.CodeReviewChangesViewModel
import com.intellij.collaboration.ui.codereview.details.model.CodeReviewChangesViewModelDelegate
import com.intellij.collaboration.util.ComputedResult
import com.intellij.collaboration.util.RefComparisonChange
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.platform.util.coroutines.childScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.github.api.data.GHCommit
import org.jetbrains.plugins.github.authentication.GHLoginSource
import org.jetbrains.plugins.github.pullrequest.data.GHPRDataContext
import org.jetbrains.plugins.github.pullrequest.data.GHPRIdentifier
import org.jetbrains.plugins.github.pullrequest.data.provider.GHPRDataProvider
import org.jetbrains.plugins.github.pullrequest.ui.GHApiLoadingErrorHandler
import org.jetbrains.plugins.github.pullrequest.ui.details.model.GHPRChangeListViewModel
import org.jetbrains.plugins.github.pullrequest.ui.details.model.GHPRChangeListViewModelImpl
import java.util.concurrent.CancellationException

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
  private val dataProvider: GHPRDataProvider,
  private val openPullRequestDiff: (GHPRIdentifier?, Boolean) -> Unit,
) : GHPRChangesViewModel {
  private val cs = parentCs.childScope()

  override val changesLoadingErrorHandler = GHApiLoadingErrorHandler(project, dataContext.securityService.account, GHLoginSource.PR_CHANGES) {
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

  private val changesContainer: StateFlow<Result<CodeReviewChangesContainer>?> =
    dataProvider.changesData.changesNeedReloadSignal.withInitial(Unit).mapNotNull {
      try {
        val changes = dataProvider.changesData.loadChanges()
        Result.success(
          CodeReviewChangesContainer(changes.changes, changes.commits.map { it.sha }, changes.changesByCommits)
        )
      }
      catch (e: CancellationException) {
        currentCoroutineContext().ensureActive()
        null
      }
      catch (e: Exception) {
        Result.failure(e)
      }
    }.stateInNow(cs, null)

  private val delegate = CodeReviewChangesViewModelDelegate.create(cs, changesContainer.filterNotNull()) { changes, changeList ->
    GHPRChangeListViewModelImpl(this, project, dataContext, dataProvider, changes, changeList, openPullRequestDiff)
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
    delegate.selectCommit(index)?.selectChange(null)
  }

  override fun selectNextCommit() {
    delegate.selectNextCommit()?.selectChange(null)
  }

  override fun selectPreviousCommit() {
    delegate.selectPreviousCommit()?.selectChange(null)
  }

  override fun selectCommit(sha: String) {
    delegate.selectCommit(sha)?.selectChange(null)
  }

  override fun selectChange(change: RefComparisonChange) {
    val commit = changesContainer.value?.getOrNull()?.let {
      it.commitsByChange[change]
    }
    delegate.selectCommit(commit)?.selectChange(change)
  }

  override fun commitHash(commit: GHCommit): String = commit.abbreviatedOid
}