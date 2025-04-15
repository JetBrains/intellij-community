// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.data

import com.intellij.collaboration.async.cancelledWith
import com.intellij.collaboration.async.launchNow
import com.intellij.collaboration.async.nestedDisposable
import com.intellij.collaboration.util.getOrNull
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.CheckedDisposable
import com.intellij.openapi.util.Disposer
import com.intellij.platform.util.coroutines.childScope
import com.intellij.util.EventDispatcher
import com.intellij.util.asSafely
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.messages.MessageBusFactory
import com.intellij.util.messages.MessageBusOwner
import com.intellij.util.messages.impl.PluginListenerDescriptor
import git4idea.remote.hosting.GitRemoteBranchesUtil
import git4idea.remote.hosting.HostedGitRepositoryRemoteBranch
import git4idea.remote.hosting.infoFlow
import git4idea.repo.GitRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.jetbrains.plugins.github.api.GithubServerPath
import org.jetbrains.plugins.github.api.data.GHIssueComment
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequest
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestReview
import org.jetbrains.plugins.github.api.data.pullrequest.timeline.GHPRTimelineItem
import org.jetbrains.plugins.github.pullrequest.data.provider.*
import org.jetbrains.plugins.github.pullrequest.data.service.*
import org.jetbrains.plugins.github.pullrequest.ui.details.model.GHPRBranchesViewModel.Companion.getHeadRemoteDescriptor
import org.jetbrains.plugins.github.util.DisposalCountingHolder
import java.util.*

internal class GHPRDataProviderRepositoryImpl(
  parentCs: CoroutineScope,
  private val repositoryService: GHPRRepositoryDataService,
  private val detailsService: GHPRDetailsService,
  private val reviewService: GHPRReviewService,
  private val filesService: GHPRFilesService,
  private val commentService: GHPRCommentService,
  private val changesService: GHPRChangesService,
  private val timelineLoaderFactory: (GHPRIdentifier) -> GHListLoader<GHPRTimelineItem>,
)
  : GHPRDataProviderRepository {
  private val cs = parentCs.childScope(javaClass.name)

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
    val providerCs = cs.childScope(GHPRDataProviderImpl::class.java.name).apply {
      cancelledWith(parentDisposable)
    }
    val messageBus = MessageBusFactory.newMessageBus(object : MessageBusOwner {
      override fun isDisposed() = parentDisposable.isDisposed

      override fun createListener(descriptor: PluginListenerDescriptor) =
        throw UnsupportedOperationException()
    })
    Disposer.register(parentDisposable, messageBus)

    val detailsData = GHPRDetailsDataProviderImpl(providerCs, detailsService, id, messageBus)
    providerCs.launchNow(Dispatchers.Main) {
      detailsData.detailsComputationFlow.mapNotNull { it.getOrNull() }.collect {
        providerDetailsLoadedEventDispatcher.multicaster.onDetailsLoaded(it)
      }
    }

    providerCs.launch {
      detailsData.launchDetailsReloadOnHeadRevChange(repositoryService.repositoryCoordinates.serverPath,
                                                     repositoryService.repositoryMapping.gitRepository)
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

/**
 * Signal details reload when PR branches hashes are changed (if there are known remote branches corresponding to PR branches)
 */
private suspend fun GHPRDetailsDataProviderImpl.launchDetailsReloadOnHeadRevChange(
  server: GithubServerPath,
  repository: GitRepository,
): Nothing {
  val remoteBranchDescriptor = loadedDetailsState.filterNotNull().map { details ->
    details.getHeadRemoteDescriptor(server)?.let {
      HostedGitRepositoryRemoteBranch(it, details.headRefName)
    }
  }.filterNotNull().distinctUntilChanged()

  combine(remoteBranchDescriptor, repository.infoFlow()) { descriptor, repoInfo ->
    GitRemoteBranchesUtil.findRemoteBranch(repoInfo, descriptor)?.let { repoInfo.remoteBranchesWithHashes[it] }?.asString()
  }.filterNotNull().distinctUntilChanged()
    .drop(1).collectLatest {
      delay(2000) // some delay to let the server consume changes
      signalDetailsNeedReload()
    }
  awaitCancellation()
}

private val GHPullRequest.refs: GHPRBranchesRefs
  get() = GHPRBranchesRefs(baseRefOid, headRefOid)