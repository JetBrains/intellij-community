// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.data

import com.intellij.collaboration.async.cancelledWith
import com.intellij.collaboration.async.classAsCoroutineName
import com.intellij.collaboration.async.launchNow
import com.intellij.collaboration.async.nestedDisposable
import com.intellij.collaboration.util.getOrNull
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.CheckedDisposable
import com.intellij.openapi.util.Disposer
import com.intellij.platform.util.coroutines.childScope
import com.intellij.util.EventDispatcher
import com.intellij.util.asSafely
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.messages.ListenerDescriptor
import com.intellij.util.messages.MessageBusFactory
import com.intellij.util.messages.MessageBusOwner
import com.intellij.vcs.log.data.DataPackChangeListener
import com.intellij.vcs.log.impl.VcsProjectLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch
import org.jetbrains.plugins.github.api.data.GHIssueComment
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequest
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestReview
import org.jetbrains.plugins.github.api.data.pullrequest.timeline.GHPRTimelineItem
import org.jetbrains.plugins.github.pullrequest.data.provider.*
import org.jetbrains.plugins.github.pullrequest.data.service.*
import org.jetbrains.plugins.github.util.DisposalCountingHolder
import java.util.*

internal class GHPRDataProviderRepositoryImpl(private val project: Project,
                                              parentCs: CoroutineScope,
                                              private val detailsService: GHPRDetailsService,
                                              private val reviewService: GHPRReviewService,
                                              private val filesService: GHPRFilesService,
                                              private val commentService: GHPRCommentService,
                                              private val changesService: GHPRChangesService,
                                              private val timelineLoaderFactory: (GHPRIdentifier) -> GHListLoader<GHPRTimelineItem>)
  : GHPRDataProviderRepository {
  private val cs = parentCs.childScope(classAsCoroutineName())

  private var isDisposed = false

  private val cache = mutableMapOf<GHPRIdentifier, DisposalCountingHolder<GHPRDataProvider>>()
  private val providerDetailsLoadedEventDispatcher = EventDispatcher.create(DetailsLoadedListener::class.java)

  @RequiresEdt
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

  @RequiresEdt
  override fun findDataProvider(id: GHPRIdentifier): GHPRDataProvider? = cache[id]?.value

  override fun dispose() {
    isDisposed = true
    cache.values.toList().forEach(Disposer::dispose)
  }

  private fun createDataProvider(parentDisposable: CheckedDisposable, id: GHPRIdentifier): GHPRDataProvider {
    val providerCs = cs.childScope(classAsCoroutineName<GHPRDataProviderImpl>()).apply {
      cancelledWith(parentDisposable)
    }
    val messageBus = MessageBusFactory.newMessageBus(object : MessageBusOwner {
      override fun isDisposed() = parentDisposable.isDisposed

      override fun createListener(descriptor: ListenerDescriptor) =
        throw UnsupportedOperationException()
    })
    Disposer.register(parentDisposable, messageBus)

    val detailsData = GHPRDetailsDataProviderImpl(providerCs, detailsService, id, messageBus)
    providerCs.launchNow(Dispatchers.Main) {
      detailsData.detailsComputationFlow.mapNotNull { it.getOrNull() }.collect {
        providerDetailsLoadedEventDispatcher.multicaster.onDetailsLoaded(it)
      }
    }

    VcsProjectLog.runWhenLogIsReady(project) {
      if (!parentDisposable.isDisposed) {
        val dataPackListener = DataPackChangeListener {
          providerCs.launch {
            detailsData.signalDetailsNeedReload()
          }
        }

        it.dataManager.addDataPackChangeListener(dataPackListener)
        Disposer.register(parentDisposable, Disposable {
          it.dataManager.removeDataPackChangeListener(dataPackListener)
        })
      }
    }

    val changesData = GHPRChangesDataProviderImpl(providerCs, changesService, { detailsData.loadDetails().refs }, id)
    val reviewData = GHPRReviewDataProviderImpl(providerCs, reviewService, changesData, id, messageBus)
    val viewedStateData = GHPRViewedStateDataProviderImpl(providerCs, filesService, id)
    val commentsData = GHPRCommentsDataProviderImpl(commentService, id, messageBus)

    providerCs.launch {
      detailsData.loadedDetailsState.distinctUntilChangedBy { it?.refs }.drop(1).collect {
        changesData.signalChangesNeedReload()
        viewedStateData.signalViewedStateNeedsReload()
      }
    }

    val timelineLoaderHolder = DisposalCountingHolder { timelineDisposable ->
      timelineLoaderFactory(id).also { loader ->
        messageBus.connect(timelineDisposable).subscribe(GHPRDataOperationsListener.TOPIC, object : GHPRDataOperationsListener {
          override fun onMetadataChanged() = loader.loadMore(true)

          override fun onCommentAdded() = loader.loadMore(true)
          override fun onCommentUpdated(commentId: String, newBody: String) {
            loader.updateData { item ->
              item.asSafely<GHIssueComment>()?.takeIf { it.id == commentId }?.copy(body = newBody)
            }
            loader.loadMore(true)
          }

          override fun onCommentDeleted(commentId: String) {
            loader.removeData { it is GHIssueComment && it.id == commentId }
            loader.loadMore(true)
          }

          override fun onReviewsChanged() = loader.loadMore(true)

          override fun onReviewUpdated(reviewId: String, newBody: String) {
            loader.updateData { item ->
              item.asSafely<GHPullRequestReview>()?.takeIf { it.id == reviewId }?.copy(body = newBody)
            }
            loader.loadMore(true)
          }
        })
        Disposer.register(timelineDisposable, loader)
      }
    }.also {
      Disposer.register(parentDisposable, it)
    }

    messageBus.connect(providerCs.nestedDisposable()).subscribe(GHPRDataOperationsListener.TOPIC, object : GHPRDataOperationsListener {
      override fun onReviewsChanged() {
        providerCs.launch {
          detailsData.signalMergeabilityNeedsReload()
        }
      }
    })

    return GHPRDataProviderImpl(
      id, detailsData, changesData, commentsData, reviewData, viewedStateData, timelineLoaderHolder
    )
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

private val GHPullRequest.refs: GHPRBranchesRefs
  get() = GHPRBranchesRefs(baseRefOid, headRefOid)