// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.ui

import com.intellij.collaboration.async.classAsCoroutineName
import com.intellij.collaboration.async.stateInNow
import com.intellij.collaboration.util.getOrNull
import com.intellij.openapi.diagnostic.logger
import com.intellij.platform.util.coroutines.childScope
import git4idea.remote.hosting.GitRemoteBranchesUtil
import git4idea.remote.hosting.infoFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
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
  private val cs = parentCs.childScope(classAsCoroutineName())

  @OptIn(ExperimentalCoroutinesApi::class)
  val updateRequired: StateFlow<Boolean> = run {
    val repository = dataContext.repositoryDataService.remoteCoordinates.repository
    val currentRevFlow = repository.infoFlow().map { it.currentRevision }
    val headRevFlow = dataProvider.detailsData.detailsComputationFlow.mapNotNull { it.getOrNull() }.map { it.headRefOid }

    /*
     * Request for the sync state between current local branch and branch state on the server.
     * Will produce false if local branch has all the commits that are recorded on the server, true otherwise.
     * Can't just do combineTransform bc it will not cancel previous computation
     */
    currentRevFlow.combine(headRevFlow) { currentRev, headRev ->
      currentRev to headRev
    }.distinctUntilChanged().transformLatest { (currentRev, headRev) ->
      when (currentRev) {
        null -> emit(false) // does not make sense to update on a no-revision head
        headRev -> emit(false)
        else -> supervisorScope {
          emit(false)
          val res = runCatching {
            !dataContext.changesService.isAncestor(headRev, currentRev)
          }
          emit(res.getOrNull() ?: false)
        }
      }
    }
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