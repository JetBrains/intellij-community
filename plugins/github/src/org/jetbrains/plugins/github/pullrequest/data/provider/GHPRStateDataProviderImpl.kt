// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.data.provider

import com.intellij.collaboration.async.CompletableFutureUtil.completionOnEdt
import com.intellij.openapi.Disposable
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.util.Disposer
import com.intellij.util.childScope
import com.intellij.util.io.await
import com.intellij.util.messages.MessageBus
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import org.jetbrains.plugins.github.pullrequest.data.GHPRIdentifier
import org.jetbrains.plugins.github.pullrequest.data.GHPRMergeabilityState
import org.jetbrains.plugins.github.pullrequest.data.service.GHPRStateService
import org.jetbrains.plugins.github.util.LazyCancellableBackgroundProcessValue
import java.util.concurrent.CompletableFuture

class GHPRStateDataProviderImpl(private val stateService: GHPRStateService,
                                private val pullRequestId: GHPRIdentifier,
                                private val messageBus: MessageBus,
                                private val detailsData: GHPRDetailsDataProvider)
  : GHPRStateDataProvider, Disposable {

  private var lastKnownBaseBranch: String? = null
  private var lastKnownBaseSha: String? = null
  private var lastKnownHeadSha: String? = null

  init {
    detailsData.addDetailsLoadedListener(this) {
      val details = detailsData.loadedDetails ?: return@addDetailsLoadedListener

      if (lastKnownBaseBranch != null && lastKnownBaseBranch != details.baseRefName) {
        reloadMergeabilityState()
      }
      lastKnownBaseBranch = details.baseRefName


      if (lastKnownBaseSha != null && lastKnownBaseSha != details.baseRefOid &&
          lastKnownHeadSha != null && lastKnownHeadSha != details.headRefOid) {
        reloadMergeabilityState()
      }
      lastKnownBaseSha = details.baseRefOid
      lastKnownHeadSha = details.headRefOid
    }
  }

  private val mergeabilityStateRequestValue = LazyCancellableBackgroundProcessValue.create { indicator ->
    detailsData.loadDetails().thenCompose { details ->
      stateService.loadMergeabilityState(indicator, pullRequestId, details.headRefOid, details.url, details.baseRefUpdateRule)
    }
  }

  override val mergeabilityState: Flow<Result<GHPRMergeabilityState>> = callbackFlow {
    val listenerDisposable = Disposer.newDisposable()
    var loaderScope = childScope()
    mergeabilityStateRequestValue.addDropEventListener(listenerDisposable) {
      loaderScope.cancel()
      loaderScope = childScope()
      loaderScope.launch {
        val result = runCatching {
          mergeabilityStateRequestValue.value.await()
        }
        ensureActive()
        send(result)
      }
    }
    val result = runCatching {
      mergeabilityStateRequestValue.value.await()
    }
    send(result)
    awaitClose {
      Disposer.dispose(listenerDisposable)
    }
  }

  override fun reloadMergeabilityState() {
    mergeabilityStateRequestValue.drop()
  }

  override fun close(progressIndicator: ProgressIndicator): CompletableFuture<Unit> =
    stateService.close(progressIndicator, pullRequestId).notifyState()

  override fun reopen(progressIndicator: ProgressIndicator): CompletableFuture<Unit> =
    stateService.reopen(progressIndicator, pullRequestId).notifyState()

  override fun markReadyForReview(progressIndicator: ProgressIndicator): CompletableFuture<Unit> =
    stateService.markReadyForReview(progressIndicator, pullRequestId).notifyState().completionOnEdt {
      mergeabilityStateRequestValue.drop()
    }

  override fun merge(progressIndicator: ProgressIndicator, commitMessage: Pair<String, String>, currentHeadRef: String)
    : CompletableFuture<Unit> = stateService.merge(progressIndicator, pullRequestId, commitMessage, currentHeadRef).notifyState()

  override fun rebaseMerge(progressIndicator: ProgressIndicator, currentHeadRef: String): CompletableFuture<Unit> =
    stateService.rebaseMerge(progressIndicator, pullRequestId, currentHeadRef).notifyState()

  override fun squashMerge(progressIndicator: ProgressIndicator, commitMessage: Pair<String, String>, currentHeadRef: String)
    : CompletableFuture<Unit> = stateService.squashMerge(progressIndicator, pullRequestId, commitMessage, currentHeadRef).notifyState()

  private fun <T> CompletableFuture<T>.notifyState(): CompletableFuture<T> =
    completionOnEdt {
      detailsData.reloadDetails()
      messageBus.syncPublisher(GHPRDataOperationsListener.TOPIC).onStateChanged()
    }

  override fun dispose() {
    mergeabilityStateRequestValue.drop()
  }
}