// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.data

import com.intellij.collaboration.async.launchNow
import com.intellij.collaboration.async.nestedDisposable
import com.intellij.collaboration.util.getOrNull
import com.intellij.openapi.util.Disposer
import com.intellij.platform.util.coroutines.childScope
import com.intellij.util.EventDispatcher
import com.intellij.util.asDisposable
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.messages.MessageBusFactory
import com.intellij.util.messages.MessageBusOwner
import com.intellij.util.messages.impl.PluginListenerDescriptor
import git4idea.remote.GitRemoteUrlCoordinates
import git4idea.remote.hosting.GitRemoteBranchesUtil
import git4idea.remote.hosting.HostedGitRepositoryRemoteBranch
import git4idea.remote.hosting.infoFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequest
import org.jetbrains.plugins.github.pullrequest.data.provider.GHPRBranchesRefs
import org.jetbrains.plugins.github.pullrequest.data.provider.GHPRChangesDataProviderImpl
import org.jetbrains.plugins.github.pullrequest.data.provider.GHPRCommentsDataProviderImpl
import org.jetbrains.plugins.github.pullrequest.data.provider.GHPRDataOperationsListener
import org.jetbrains.plugins.github.pullrequest.data.provider.GHPRDataProvider
import org.jetbrains.plugins.github.pullrequest.data.provider.GHPRDataProviderImpl
import org.jetbrains.plugins.github.pullrequest.data.provider.GHPRDetailsDataProviderImpl
import org.jetbrains.plugins.github.pullrequest.data.provider.GHPRReviewDataProviderImpl
import org.jetbrains.plugins.github.pullrequest.data.provider.GHPRTimelineDataProviderImpl
import org.jetbrains.plugins.github.pullrequest.data.provider.GHPRViewedStateDataProviderImpl
import org.jetbrains.plugins.github.pullrequest.data.provider.detailsComputationFlow
import org.jetbrains.plugins.github.pullrequest.data.service.GHPRChangesService
import org.jetbrains.plugins.github.pullrequest.data.service.GHPRCommentService
import org.jetbrains.plugins.github.pullrequest.data.service.GHPRDetailsService
import org.jetbrains.plugins.github.pullrequest.data.service.GHPRFilesService
import org.jetbrains.plugins.github.pullrequest.data.service.GHPRRepositoryDataService
import org.jetbrains.plugins.github.pullrequest.data.service.GHPRReviewService
import org.jetbrains.plugins.github.pullrequest.data.service.GHPRTimelineService
import org.jetbrains.plugins.github.pullrequest.ui.details.model.GHPRBranchesViewModel.Companion.getHeadRemoteDescriptor
import org.jetbrains.plugins.github.util.AcquirableScopedValueOwner
import java.util.EventListener
import kotlin.time.Duration.Companion.milliseconds

internal class GHPRDataProviderRepositoryImpl(
  parentCs: CoroutineScope,
  private val repositoryService: GHPRRepositoryDataService,
  private val detailsService: GHPRDetailsService,
  private val timelineService: GHPRTimelineService,
  private val reviewService: GHPRReviewService,
  private val filesService: GHPRFilesService,
  private val commentService: GHPRCommentService,
  private val changesService: GHPRChangesService,
) : GHPRDataProviderRepository {
  private val cs = parentCs.childScope(javaClass.name)

  private val cache = mutableMapOf<GHPRIdentifier, AcquirableScopedValueOwner<GHPRDataProvider>>()
  private val providerDetailsLoadedEventDispatcher = EventDispatcher.create(DetailsLoadedListener::class.java)

  @RequiresEdt
  override fun getDataProvider(id: GHPRIdentifier, hostCs: CoroutineScope): GHPRDataProvider =
    cache.getOrPut(id) {
      AcquirableScopedValueOwner(cs) {
        createDataProvider(id)
      }
    }.acquireValue(hostCs)

  @RequiresEdt
  override fun findDataProvider(id: GHPRIdentifier): GHPRDataProvider? = cache[id]?.value

  private fun CoroutineScope.createDataProvider(id: GHPRIdentifier): GHPRDataProvider {
    val cs = this
    val messageBus = MessageBusFactory.newMessageBus (object : MessageBusOwner {
      override fun isDisposed() = !cs.isActive

      override fun createListener(descriptor: PluginListenerDescriptor) =
        throw UnsupportedOperationException()
    }).also { Disposer.register(cs.asDisposable(), it) }

    val providerCs = cs.childScope(GHPRDataProviderImpl::class.java.name)
    val detailsData = GHPRDetailsDataProviderImpl(providerCs, detailsService, id, messageBus)
    providerCs.launchNow(Dispatchers.Main) {
      detailsData.detailsComputationFlow.mapNotNull { it.getOrNull() }.collect {
        providerDetailsLoadedEventDispatcher.multicaster.onDetailsLoaded(it)
      }
    }

    providerCs.launch {
      detailsData.launchDetailsReloadOnHeadRevChange(repositoryService.remoteCoordinates)
    }

    val timelineData = GHPRTimelineDataProviderImpl(providerCs, timelineService, messageBus, id)
    val changesData = GHPRChangesDataProviderImpl(providerCs, changesService, { detailsData.loadDetails().refs }, id)
    val reviewData = GHPRReviewDataProviderImpl(providerCs, reviewService, changesData, id, messageBus)
    val viewedStateData = GHPRViewedStateDataProviderImpl(providerCs, filesService, id)
    val commentsData = GHPRCommentsDataProviderImpl(commentService, id, messageBus)

    providerCs.launch {
      // filterNotNull(): without it, the first details load (null -> details) itself counts as a "refs
      // changed" transition, spuriously cancelling a changes load already in flight for those same refs.
      detailsData.loadedDetailsState.filterNotNull().distinctUntilChangedBy { it.refs }.drop(1).collect {
        changesData.signalChangesNeedReload()
        viewedStateData.signalViewedStateNeedsReload()
      }
    }

    messageBus.connect(providerCs.nestedDisposable()).subscribe(GHPRDataOperationsListener.TOPIC, object : GHPRDataOperationsListener {
      override fun onReviewsChanged() {
        providerCs.launch {
          detailsData.signalDetailsNeedReload()
          detailsData.signalMergeabilityNeedsReload()
        }
      }
    })

    return GHPRDataProviderImpl(
      id, detailsData, timelineData, changesData, commentsData, reviewData, viewedStateData
    )
  }

  override fun addDetailsLoadedListener(hostCs: CoroutineScope, listener: (GHPullRequest) -> Unit) {
    providerDetailsLoadedEventDispatcher.addListener(object : DetailsLoadedListener {
      override fun onDetailsLoaded(details: GHPullRequest) {
        listener(details)
      }
    }, hostCs.asDisposable())
  }

  private interface DetailsLoadedListener : EventListener {
    fun onDetailsLoaded(details: GHPullRequest)
  }
}

/**
 * Signal details reload when PR branches hashes are changed (if there are known remote branches corresponding to PR branches)
 */
private suspend fun GHPRDetailsDataProviderImpl.launchDetailsReloadOnHeadRevChange(
  gitRemoteUrlCoordinates: GitRemoteUrlCoordinates,
): Nothing {

  val remoteBranchDescriptor = loadedDetailsState.filterNotNull().mapNotNull { details ->
    details.getHeadRemoteDescriptor(gitRemoteUrlCoordinates)?.let {
      HostedGitRepositoryRemoteBranch(it, details.headRefName)
    }
  }.distinctUntilChanged()

  combine(remoteBranchDescriptor, gitRemoteUrlCoordinates.repository.infoFlow()) { descriptor, repoInfo ->
    GitRemoteBranchesUtil.findRemoteBranch(repoInfo, descriptor)?.let { repoInfo.remoteBranchesWithHashes[it] }?.asString()
  }.filterNotNull().distinctUntilChanged()
    .drop(1).collectLatest {
      delay(2000.milliseconds) // some delay to let the server consume changes
      signalDetailsNeedReload()
    }
  awaitCancellation()
}

private val GHPullRequest.refs: GHPRBranchesRefs
  get() = GHPRBranchesRefs(baseRefOid, headRefOid)