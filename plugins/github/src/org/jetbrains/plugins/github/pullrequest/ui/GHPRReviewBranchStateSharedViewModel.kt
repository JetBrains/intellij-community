// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.ui

import com.intellij.collaboration.async.CompletableFutureUtil
import com.intellij.collaboration.async.classAsCoroutineName
import com.intellij.collaboration.async.stateInNow
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
import org.jetbrains.plugins.github.pullrequest.data.provider.GHPRDataProvider
import org.jetbrains.plugins.github.pullrequest.ui.details.model.GHPRBranchesViewModel.Companion.getHeadRemoteDescriptor

private val LOG = logger<GHPRReviewBranchStateSharedViewModel>()

internal class GHPRReviewBranchStateSharedViewModel(
  parentCs: CoroutineScope,
  private val dataContext: GHPRDataContext,
  private val dataProvider: GHPRDataProvider
) {
  private val cs = parentCs.childScope(classAsCoroutineName())
  val updateRequired: StateFlow<Boolean> = dataProvider.changesData.newChangesInReviewRequest.transform {
    val result = runCatching {
      it.await()
    }.fold({ it }, { false })
    emit(result)
  }.stateInNow(cs, false)

  private val _updateErrors = MutableSharedFlow<Exception>()
  val updateErrors: SharedFlow<Exception> = _updateErrors.asSharedFlow()

  fun updateBranch() {
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
    val remoteDescriptor = details.getHeadRemoteDescriptor(server) ?: return

    val repository = dataContext.repositoryDataService.remoteCoordinates.repository
    val localPrefix = if (details.headRepository?.isFork == true) "fork" else null
    GitRemoteBranchesUtil.fetchAndCheckoutRemoteBranch(repository, remoteDescriptor, details.headRefName, localPrefix)
  }
}