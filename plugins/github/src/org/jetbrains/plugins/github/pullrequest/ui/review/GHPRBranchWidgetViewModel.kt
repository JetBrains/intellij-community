// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.ui.review

import com.intellij.collaboration.async.CompletableFutureUtil
import com.intellij.collaboration.async.classAsCoroutineName
import com.intellij.collaboration.async.computationState
import com.intellij.collaboration.async.stateInNow
import com.intellij.collaboration.util.ComputedResult
import com.intellij.openapi.diagnostic.logger
import com.intellij.platform.util.coroutines.childScope
import git4idea.remote.hosting.GitRemoteBranchesUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.future.asDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.plugins.github.pullrequest.data.GHPRDataContext
import org.jetbrains.plugins.github.pullrequest.data.GHPRIdentifier
import org.jetbrains.plugins.github.pullrequest.data.provider.GHPRDataProvider
import org.jetbrains.plugins.github.pullrequest.data.provider.changesRequestFlow
import org.jetbrains.plugins.github.pullrequest.ui.details.model.GHPRBranchesViewModel.Companion.getRemoteDescriptor
import org.jetbrains.plugins.github.pullrequest.ui.toolwindow.model.GHPRToolWindowProjectViewModel

interface GHPRBranchWidgetViewModel {
  val id: GHPRIdentifier

  val updateRequired: StateFlow<Boolean>
  val dataLoadingState: StateFlow<ComputedResult<Any>>

  val updateErrors: SharedFlow<Exception>

  fun showPullRequest()
  fun updateBranch()
}

private val LOG = logger<GHPRBranchWidgetViewModelImpl>()

internal class GHPRBranchWidgetViewModelImpl(
  parentCs: CoroutineScope,
  private val dataContext: GHPRDataContext,
  private val dataProvider: GHPRDataProvider,
  private val projectVm: GHPRToolWindowProjectViewModel,
  override val id: GHPRIdentifier
) : GHPRBranchWidgetViewModel {
  private val cs = parentCs.childScope(classAsCoroutineName())

  override val updateRequired: StateFlow<Boolean> = dataProvider.changesData.newChangesInReviewRequest.transform {
    val result = runCatching {
      it.await()
    }.fold({ it }, { false })
    emit(result)
  }.stateInNow(cs, false)

  override val dataLoadingState: StateFlow<ComputedResult<Any>> =
    dataProvider.changesData.changesRequestFlow().computationState().stateInNow(cs, ComputedResult.loading())

  private val _updateErrors = MutableSharedFlow<Exception>()
  override val updateErrors: SharedFlow<Exception> = _updateErrors.asSharedFlow()

  override fun showPullRequest() {
    projectVm.viewPullRequest(id)
  }

  override fun updateBranch() {
    cs.launch {
      doUpdateBranch()
    }
  }

  private suspend fun doUpdateBranch() {
    val details = try {
      val detailsData = dataProvider.detailsData
      withContext(Dispatchers.Main) {
        detailsData.reloadDetails()
        detailsData.loadDetails().asDeferred()
      }.await()
    }
    catch (e: Exception) {
      if (!CompletableFutureUtil.isCancellation(e)) {
        LOG.warn("Pull request branch update failed", e)
        _updateErrors.emit(e)
      }
      return
    }
    val server = dataContext.repositoryDataService.repositoryCoordinates.serverPath
    val remoteDescriptor = details.getRemoteDescriptor(server) ?: return

    val repository = dataContext.repositoryDataService.remoteCoordinates.repository
    val localPrefix = if (details.headRepository?.isFork == true) "fork" else null
    GitRemoteBranchesUtil.fetchAndCheckoutRemoteBranch(repository, remoteDescriptor, details.headRefName, localPrefix)
  }
}
