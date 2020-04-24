// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.data

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.util.EventDispatcher
import org.jetbrains.annotations.CalledInAwt
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequest
import org.jetbrains.plugins.github.pullrequest.data.provider.*
import org.jetbrains.plugins.github.pullrequest.data.service.*
import java.util.*

internal class GHPRDataLoaderImpl(private val detailsService: GHPRDetailsService,
                                  private val stateService: GHPRStateService,
                                  private val reviewService: GHPRReviewService,
                                  private val commentService: GHPRCommentService,
                                  private val changesService: GHPRChangesService,
                                  private val timelineLoaderFactory: (GHPRIdentifier) -> GHPRTimelineLoader)
  : GHPRDataLoader {

  private var isDisposed = false

  private val cache = mutableMapOf<GHPRIdentifier, DisposalCountingHolder>()
  private val providerDetailsLoadedEventDispatcher = EventDispatcher.create(DetailsLoadedListener::class.java)

  @CalledInAwt
  override fun getDataProvider(id: GHPRIdentifier, disposable: Disposable): GHPRDataProvider {
    if (isDisposed) throw IllegalStateException("Already disposed")

    return cache.getOrPut(id) {
      DisposalCountingHolder(createDataProvider(id)).also {
        Disposer.register(it, Disposable { cache.remove(id) })
      }
    }.acquire(disposable)
  }

  private fun createDataProvider(id: GHPRIdentifier): GHPRDataProvider {
    val detailsData = GHPRDetailsDataProviderImpl(detailsService, id).apply {
      addDetailsLoadedListener(this) {
        loadedDetails?.let { providerDetailsLoadedEventDispatcher.multicaster.onDetailsLoaded(it) }
      }
    }
    val stateData = GHPRStateDataProviderImpl(stateService, id, detailsData)
    val changesData = GHPRChangesDataProviderImpl(changesService, id, detailsData)
    val reviewData = GHPRReviewDataProviderImpl(reviewService, id)
    val commentsData = GHPRCommentsDataProviderImpl(commentService, id)
    val timelineLoaderHolder = GHPRCountingTimelineLoaderHolder {
      timelineLoaderFactory(id)
    }
    return GHPRDataProviderImpl(detailsData, stateData, changesData, commentsData, reviewData, timelineLoaderHolder).also {
      Disposer.register(it, detailsData)
      Disposer.register(it, stateData)
      Disposer.register(it, changesData)
      Disposer.register(it, reviewData)
    }
  }

  @CalledInAwt
  override fun findDataProvider(id: GHPRIdentifier): GHPRDataProvider? = cache[id]?.provider

  override fun dispose() {
    isDisposed = true
    cache.values.toList().forEach(Disposer::dispose)
  }

  private class DisposalCountingHolder(val provider: GHPRDataProvider) : Disposable {

    private var disposalCounter = 0

    fun acquire(disposable: Disposable): GHPRDataProvider {
      disposalCounter++
      Disposer.register(disposable, Disposable {
        disposalCounter--
        if (disposalCounter <= 0) {
          Disposer.dispose(this)
        }
      })
      return provider
    }

    override fun dispose() {
      Disposer.dispose(provider)
    }
  }

  override fun addDetailsLoadedListener(listener: (GHPullRequest) -> Unit) {
    providerDetailsLoadedEventDispatcher.addListener(object : DetailsLoadedListener {
      override fun onDetailsLoaded(details: GHPullRequest) {
        listener(details)
      }
    })
  }

  private interface DetailsLoadedListener : EventListener {
    fun onDetailsLoaded(details: GHPullRequest)
  }
}