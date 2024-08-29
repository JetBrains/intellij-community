// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.ui

import com.intellij.collaboration.async.stateInNow
import com.intellij.collaboration.util.getOrNull
import com.intellij.openapi.diagnostic.logger
import com.intellij.platform.util.coroutines.childScope
import git4idea.remote.hosting.GitRemoteBranchesUtil
import git4idea.remote.hosting.isInCurrentHistory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.jetbrains.plugins.github.pullrequest.data.GHPRDataContext
import org.jetbrains.plugins.github.pullrequest.data.provider.GHPRDataProvider
import org.jetbrains.plugins.github.pullrequest.data.provider.detailsComputationFlow
import org.jetbrains.plugins.github.pullrequest.ui.details.model.GHPRBranchesViewModel.Companion.getHeadRemoteDescriptor
import kotlin.coroutines.cancellation.CancellationException

private val LOG = logger<GHPRReviewBranchStateSharedViewModel>()

internal class GHPRReviewBranchStateSharedViewModel(
  parentCs: CoroutineScope,
  private val dataContext: GHPRDataContext,
  private val dataProvider: GHPRDataProvider
) {
  private val cs = parentCs.childScope(javaClass.name)

  private val repository = dataContext.repositoryDataService.remoteCoordinates.repository

  val updateRequired: StateFlow<Boolean> =
    repository.isInCurrentHistory(
      rev = dataProvider.detailsData.detailsComputationFlow.mapNotNull { it.getOrNull() }.map { it.headRefOid }
    ).map { it?.not() ?: false }.stateInNow(cs, false)

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
      detailsData.signalDetailsNeedReload()
      detailsData.loadDetails()
    }
    catch (ce: CancellationException) {
      throw ce
    }
    catch (e: Exception) {
      LOG.warn("Pull request branch update failed", e)
      _updateErrors.emit(e)
      return
    }
    val server = dataContext.repositoryDataService.repositoryCoordinates.serverPath
    val remoteDescriptor = details.getHeadRemoteDescriptor(server) ?: return

    val repository = dataContext.repositoryDataService.remoteCoordinates.repository
    val localPrefix = if (details.headRepository?.isFork == true) "fork" else null
    GitRemoteBranchesUtil.fetchAndCheckoutRemoteBranch(repository, remoteDescriptor, details.headRefName, localPrefix)
  }
}