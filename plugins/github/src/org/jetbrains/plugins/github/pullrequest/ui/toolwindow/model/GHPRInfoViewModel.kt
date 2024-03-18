// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.ui.toolwindow.model

import com.intellij.collaboration.util.ComputedResult
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.platform.util.coroutines.childScope
import com.intellij.util.io.await
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequest
import org.jetbrains.plugins.github.pullrequest.data.GHPRDataContext
import org.jetbrains.plugins.github.pullrequest.data.GHPRIdentifier
import org.jetbrains.plugins.github.pullrequest.data.provider.GHPRDataProvider
import org.jetbrains.plugins.github.pullrequest.ui.GHApiLoadingErrorHandler
import org.jetbrains.plugins.github.pullrequest.ui.details.model.GHPRDetailsLoadingViewModel
import org.jetbrains.plugins.github.pullrequest.ui.details.model.impl.GHPRDetailsViewModel
import org.jetbrains.plugins.github.pullrequest.ui.details.model.impl.GHPRDetailsViewModelImpl
import java.util.concurrent.CompletableFuture

@ApiStatus.Experimental
class GHPRInfoViewModel internal constructor(
  private val project: Project,
  parentCs: CoroutineScope,
  private val dataContext: GHPRDataContext,
  private val dataProvider: GHPRDataProvider
) : GHPRDetailsLoadingViewModel {
  private val cs = parentCs.childScope()

  val pullRequest: GHPRIdentifier = dataProvider.id
  val pullRequestUrl: String? get() = dataProvider.detailsData.loadedDetails?.url

  override val detailsVm: StateFlow<ComputedResult<GHPRDetailsViewModel>>

  init {
    val detailsRequestsFlow: Flow<CompletableFuture<GHPullRequest>> = callbackFlow {
      val listenerDisposable = Disposer.newDisposable()
      dataProvider.detailsData.loadDetails(listenerDisposable) {
        launch { send(it) }
      }
      awaitClose {
        Disposer.dispose(listenerDisposable)
      }
    }

    detailsVm = channelFlow<ComputedResult<GHPRDetailsViewModel>> {
      var vm: GHPRDetailsViewModelImpl? = null
      detailsRequestsFlow.collectLatest {
        if (vm == null) {
          send(ComputedResult.loading())
        }

        vm?.isUpdating?.value = true
        val toSend = try {
          val details = it.await()
          val currentVm = vm
          if (currentVm == null) {
            val newVm = GHPRDetailsViewModelImpl(project, cs.childScope(), dataContext, dataProvider, details)
            vm = newVm
            ComputedResult.success(newVm)
          }
          else {
            currentVm.update(details)
            null
          }
        }
        catch (ce: CancellationException) {
          vm?.destroy()
          vm = null
          ComputedResult.loading()
        }
        catch (e: Throwable) {
          vm?.destroy()
          vm = null
          ComputedResult.failure(e)
        }
        finally {
          vm?.isUpdating?.value = false
        }
        if (toSend != null) {
          send(toSend)
        }
      }

    }.stateIn(cs, SharingStarted.Lazily, ComputedResult.loading())
  }

  val detailsLoadingErrorHandler: GHApiLoadingErrorHandler = GHApiLoadingErrorHandler(project, dataContext.securityService.account) {
    dataProvider.detailsData.reloadDetails()
  }

  override fun requestReload() {
    dataProvider.detailsData.reloadDetails()
    dataProvider.stateData.reloadMergeabilityState()
    dataProvider.reviewData.resetPendingReview()
    dataProvider.changesData.reloadChanges()
    dataProvider.viewedStateData.reset()
  }
}