// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.data.provider

import com.intellij.collaboration.util.CollectionDelta
import com.intellij.platform.util.coroutines.childScope
import com.intellij.util.messages.MessageBus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequest
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestRequestedReviewer
import org.jetbrains.plugins.github.pullrequest.data.GHPRIdentifier
import org.jetbrains.plugins.github.pullrequest.data.GHPRMergeabilityState
import org.jetbrains.plugins.github.pullrequest.data.service.GHPRDetailsService

internal class GHPRDetailsDataProviderImpl(parentCs: CoroutineScope,
                                           private val detailsService: GHPRDetailsService,
                                           private val pullRequestId: GHPRIdentifier,
                                           private val messageBus: MessageBus)
  : GHPRDetailsDataProvider {
  private val cs = parentCs.childScope(javaClass.name)

  private val _loadedDetailsState = MutableStateFlow<GHPullRequest?>(null)
  val loadedDetailsState = _loadedDetailsState.asStateFlow()

  override val loadedDetails: GHPullRequest?
    get() = loadedDetailsState.value

  override val stateChangeSignal = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

  private val detailsLoader = LoaderWithMutableCache(cs) { detailsService.loadDetails(pullRequestId) }
  override val detailsNeedReloadSignal = detailsLoader.updatedSignal

  override suspend fun loadDetails(): GHPullRequest = detailsLoader.load().also {
    _loadedDetailsState.value = it
  }

  private val mergeabilityLoader = LoaderWithMutableCache(cs) { detailsService.loadMergeabilityState(pullRequestId) }
  override val mergeabilityNeedsReloadSignal = mergeabilityLoader.updatedSignal

  override suspend fun loadMergeabilityState(): GHPRMergeabilityState = mergeabilityLoader.load()

  override suspend fun updateDetails(title: String?, description: String?): GHPullRequest {
    val details = detailsService.updateDetails(pullRequestId, title, description)
    withContext(NonCancellable) {
      detailsLoader.overrideResult(details)
      withContext(Dispatchers.Main) {
        messageBus.syncPublisher(GHPRDataOperationsListener.TOPIC).onMetadataChanged()
      }
    }
    return details
  }

  override suspend fun adjustReviewers(delta: CollectionDelta<GHPullRequestRequestedReviewer>) {
    try {
      detailsService.adjustReviewers(pullRequestId, delta)
    }
    finally {
      notifyNeedsReload(false)
    }
  }

  override suspend fun close() =
    try {
      detailsService.close(pullRequestId)
    }
    finally {
      notifyNeedsReload()
    }

  override suspend fun reopen() =
    try {
      detailsService.reopen(pullRequestId)
    }
    finally {
      notifyNeedsReload()
    }

  override suspend fun markReadyForReview() =
    try {
      detailsService.markReadyForReview(pullRequestId)
    }
    finally {
      notifyNeedsReload()
    }

  override suspend fun merge(commitMessage: Pair<String, String>, currentHeadRef: String) =
    try {
      detailsService.merge(pullRequestId, commitMessage, currentHeadRef)
    }
    finally {
      notifyNeedsReload()
    }

  override suspend fun rebaseMerge(currentHeadRef: String) =
    try {
      detailsService.rebaseMerge(pullRequestId, currentHeadRef)
    }
    finally {
      notifyNeedsReload()
    }

  override suspend fun squashMerge(commitMessage: Pair<String, String>, currentHeadRef: String) =
    try {
      detailsService.squashMerge(pullRequestId, commitMessage, currentHeadRef)
    }
    finally {
      notifyNeedsReload()
    }

  private suspend fun notifyNeedsReload(state: Boolean = true) {
    withContext(NonCancellable) {
      signalDetailsNeedReload()
      signalMergeabilityNeedsReload()
      withContext(Dispatchers.Main) {
        messageBus.syncPublisher(GHPRDataOperationsListener.TOPIC).onMetadataChanged()
      }
      if (state) {
        stateChangeSignal.tryEmit(Unit)
      }
    }
  }

  override suspend fun signalDetailsNeedReload() {
    detailsLoader.clearCache()
  }

  override suspend fun signalMergeabilityNeedsReload() {
    mergeabilityLoader.clearCache()
  }
}