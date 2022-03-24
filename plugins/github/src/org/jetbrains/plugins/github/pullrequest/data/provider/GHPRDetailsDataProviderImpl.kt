// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.data.provider

import com.intellij.collaboration.async.CompletableFutureUtil.completionOnEdt
import com.intellij.collaboration.async.CompletableFutureUtil.successOnEdt
import com.intellij.openapi.Disposable
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.util.EventDispatcher
import com.intellij.util.messages.MessageBus
import org.jetbrains.plugins.github.api.data.GHLabel
import org.jetbrains.plugins.github.api.data.GHUser
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequest
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestRequestedReviewer
import org.jetbrains.plugins.github.pullrequest.data.GHPRIdentifier
import org.jetbrains.plugins.github.pullrequest.data.service.GHPRCommentService
import org.jetbrains.plugins.github.pullrequest.data.service.GHPRDetailsService
import com.intellij.collaboration.ui.SimpleEventListener
import org.jetbrains.plugins.github.util.CollectionDelta
import org.jetbrains.plugins.github.util.LazyCancellableBackgroundProcessValue
import java.util.concurrent.CompletableFuture
import kotlin.properties.Delegates

class GHPRDetailsDataProviderImpl(private val detailsService: GHPRDetailsService,
                                  private val pullRequestId: GHPRIdentifier,
                                  private val messageBus: MessageBus)
  : GHPRDetailsDataProvider, Disposable {

  private val detailsLoadedEventDispatcher = EventDispatcher.create(SimpleEventListener::class.java)

  override var loadedDetails by Delegates.observable<GHPullRequest?>(null) { _, _, _ ->
    detailsLoadedEventDispatcher.multicaster.eventOccurred()
  }
    private set

  private val detailsRequestValue = LazyCancellableBackgroundProcessValue.create { indicator ->
    detailsService.loadDetails(indicator, pullRequestId).successOnEdt {
      loadedDetails = it
      it
    }
  }

  override fun loadDetails(): CompletableFuture<GHPullRequest> = detailsRequestValue.value

  override fun reloadDetails() = detailsRequestValue.drop()

  override fun updateDetails(indicator: ProgressIndicator, title: String?, description: String?): CompletableFuture<GHPullRequest> {
    val future = detailsService.updateDetails(indicator, pullRequestId, title, description).completionOnEdt {
      messageBus.syncPublisher(GHPRDataOperationsListener.TOPIC).onMetadataChanged()
    }
    detailsRequestValue.overrideProcess(future.successOnEdt {
      loadedDetails = it
      it
    })
    return future
  }

  override fun adjustReviewers(indicator: ProgressIndicator,
                               delta: CollectionDelta<GHPullRequestRequestedReviewer>): CompletableFuture<Unit> {
    return detailsService.adjustReviewers(indicator, pullRequestId, delta).notify()
  }

  override fun adjustAssignees(indicator: ProgressIndicator, delta: CollectionDelta<GHUser>): CompletableFuture<Unit> {
    return detailsService.adjustAssignees(indicator, pullRequestId, delta).notify()
  }

  override fun adjustLabels(indicator: ProgressIndicator, delta: CollectionDelta<GHLabel>): CompletableFuture<Unit> {
    return detailsService.adjustLabels(indicator, pullRequestId, delta).notify()
  }

  override fun addDetailsReloadListener(disposable: Disposable, listener: () -> Unit) =
    detailsRequestValue.addDropEventListener(disposable, listener)

  override fun addDetailsLoadedListener(disposable: Disposable, listener: () -> Unit) =
    SimpleEventListener.addDisposableListener(detailsLoadedEventDispatcher, disposable, listener)

  private fun <T> CompletableFuture<T>.notify(): CompletableFuture<T> =
    completionOnEdt {
      detailsRequestValue.drop()
      messageBus.syncPublisher(GHPRDataOperationsListener.TOPIC).onMetadataChanged()
    }

  override fun dispose() {
    detailsRequestValue.drop()
  }
}