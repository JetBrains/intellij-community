// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.data

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.util.EventDispatcher
import com.intellij.util.messages.ListenerDescriptor
import com.intellij.util.messages.MessageBusFactory
import com.intellij.util.messages.MessageBusOwner
import org.jetbrains.annotations.CalledInAwt
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequest
import org.jetbrains.plugins.github.api.data.pullrequest.timeline.GHPRTimelineItem
import org.jetbrains.plugins.github.pullrequest.GHPRDiffControllerImpl
import org.jetbrains.plugins.github.pullrequest.data.provider.*
import org.jetbrains.plugins.github.pullrequest.data.service.*
import org.jetbrains.plugins.github.util.DisposalCountingHolder
import java.util.*

internal class GHPRDataProviderRepositoryImpl(private val detailsService: GHPRDetailsService,
                                              private val stateService: GHPRStateService,
                                              private val reviewService: GHPRReviewService,
                                              private val commentService: GHPRCommentService,
                                              private val changesService: GHPRChangesService,
                                              private val timelineLoaderFactory: (GHPRIdentifier) -> GHListLoader<GHPRTimelineItem>)
  : GHPRDataProviderRepository {

  private var isDisposed = false

  private val cache = mutableMapOf<GHPRIdentifier, DisposalCountingHolder<GHPRDataProvider>>()
  private val providerDetailsLoadedEventDispatcher = EventDispatcher.create(DetailsLoadedListener::class.java)

  @CalledInAwt
  override fun getDataProvider(id: GHPRIdentifier, disposable: Disposable): GHPRDataProvider {
    if (isDisposed) throw IllegalStateException("Already disposed")

    return cache.getOrPut(id) {
      DisposalCountingHolder {
        createDataProvider(it, id)
      }.also {
        Disposer.register(it, Disposable { cache.remove(id) })
      }
    }.acquireValue(disposable)
  }

  @CalledInAwt
  override fun findDataProvider(id: GHPRIdentifier): GHPRDataProvider? = cache[id]?.value

  override fun dispose() {
    isDisposed = true
    cache.values.toList().forEach(Disposer::dispose)
  }

  private fun createDataProvider(parentDisposable: Disposable, id: GHPRIdentifier): GHPRDataProvider {
    val messageBus = MessageBusFactory.newMessageBus(object : MessageBusOwner {
      override fun isDisposed() = Disposer.isDisposed(parentDisposable)

      override fun createListener(descriptor: ListenerDescriptor) =
        throw UnsupportedOperationException()
    })
    Disposer.register(parentDisposable, messageBus)

    val detailsData = GHPRDetailsDataProviderImpl(detailsService, id, messageBus).apply {
      addDetailsLoadedListener(parentDisposable) {
        loadedDetails?.let { providerDetailsLoadedEventDispatcher.multicaster.onDetailsLoaded(it) }
      }
    }.also {
      Disposer.register(parentDisposable, it)
    }

    val stateData = GHPRStateDataProviderImpl(stateService, id, messageBus, detailsData).also {
      Disposer.register(parentDisposable, it)
    }
    val changesData = GHPRChangesDataProviderImpl(changesService, id, detailsData).also {
      Disposer.register(parentDisposable, it)
    }
    val reviewData = GHPRReviewDataProviderImpl(commentService, reviewService, id, messageBus).also {
      Disposer.register(parentDisposable, it)
    }
    val commentsData = GHPRCommentsDataProviderImpl(commentService, id, messageBus)

    val timelineLoaderHolder = DisposalCountingHolder { timelineDisposable ->
      timelineLoaderFactory(id).also {
        messageBus.connect(timelineDisposable).subscribe(GHPRDataOperationsListener.TOPIC, object : GHPRDataOperationsListener {
          override fun onStateChanged() = it.loadMore(true)
          override fun onMetadataChanged() = it.loadMore(true)
          override fun onCommentAdded() = it.loadMore(true)
          override fun onReviewsChanged() = it.loadMore(true)
        })
        Disposer.register(timelineDisposable, it)
      }
    }.also {
      Disposer.register(parentDisposable, it)
    }

    messageBus.connect(stateData).subscribe(GHPRDataOperationsListener.TOPIC, object : GHPRDataOperationsListener {
      override fun onReviewsChanged() = stateData.reloadMergeabilityState()
    })

    return GHPRDataProviderImpl(id, detailsData, stateData, changesData, commentsData, reviewData, timelineLoaderHolder,
                                GHPRDiffControllerImpl())
  }

  override fun addDetailsLoadedListener(disposable: Disposable, listener: (GHPullRequest) -> Unit) {
    providerDetailsLoadedEventDispatcher.addListener(object : DetailsLoadedListener {
      override fun onDetailsLoaded(details: GHPullRequest) {
        listener(details)
      }
    }, disposable)
  }

  private interface DetailsLoadedListener : EventListener {
    fun onDetailsLoaded(details: GHPullRequest)
  }
}