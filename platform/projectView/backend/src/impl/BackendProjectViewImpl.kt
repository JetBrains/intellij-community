// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:OptIn(ExperimentalAtomicApi::class)

package com.intellij.platform.projectView.backend.impl

import com.intellij.ide.projectView.impl.isProjectViewSplitAsync
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.platform.projectView.ProjectViewPanes
import com.intellij.platform.projectView.ProjectViewPanesImpl
import com.intellij.platform.projectView.impl.BackendProjectView
import com.intellij.platform.projectView.pane.ProjectViewPaneId
import com.intellij.platform.projectView.rpc.SelectPaneRequestDTO
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.concurrent.atomics.AtomicLong
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.incrementAndFetch

/**
 * Mirrors the panes of the frontend tool window, and sends the pane selection requests to the frontend.
 *
 * The frontend decides each request and sends the result back.
 * A later request of the backend supersedes the pending one at once.
 */
internal class BackendProjectViewImpl(private val coroutineScope: CoroutineScope) : BackendProjectView {
  companion object {
    fun getInstance(project: Project): BackendProjectViewImpl = project.service<BackendProjectView>() as BackendProjectViewImpl
  }

  override val panes: StateFlow<ProjectViewPanes>
    field = MutableStateFlow<ProjectViewPanes>(ProjectViewPanesImpl.EMPTY)

  /**
   * The request that waits for its result from the frontend, or `null`.
   */
  private val pendingRequest = MutableStateFlow<SelectPaneRequest?>(null)
  private val lastRequestId = AtomicLong(0)

  private val panesLoopMutex = Mutex()

  /**
   * The loop that receives the panes from the latest channel, or `null` before the first channel.
   * Accessed only under [panesLoopMutex].
   */
  private var panesLoop: Job? = null

  override suspend fun selectPane(paneId: ProjectViewPaneId): Boolean {
    if (!isProjectViewSplitAsync()) {
      LOG.debug { "Not selecting the pane $paneId, because the split Project View is off" }
      return false
    }
    val request = SelectPaneRequest(lastRequestId.incrementAndFetch(), paneId)
    val supersededRequest = pendingRequest.getAndUpdate { request }
    if (supersededRequest != null) {
      LOG.debug { "$request supersedes $supersededRequest" }
      supersededRequest.result.complete(false)
    }
    try {
      return request.result.await()
    }
    finally {
      // Withdraws the request if the caller is canceled.
      pendingRequest.compareAndSet(request, null)
    }
  }

  fun getSelectPaneRequestFlow(): Flow<SelectPaneRequestDTO?> {
    return pendingRequest.map { request -> request?.toDTO() }
  }

  fun completeSelectPaneRequest(requestId: Long, isSelected: Boolean) {
    val request = pendingRequest.value
    if (request?.id != requestId) {
      LOG.debug { "Ignoring the result of the request $requestId, because it isn't pending" }
      return
    }
    LOG.debug { "$request is complete, isSelected = $isSelected" }
    request.result.complete(isSelected)
    pendingRequest.compareAndSet(request, null)
  }

  /**
   * Returns a new channel for the panes of the frontend.
   *
   * The loop of the previous channel finishes before the loop of the new channel starts.
   * When a channel closes, the mirrored panes stay until a new channel receives new panes.
   */
  suspend fun createPanesChannel(): SendChannel<ProjectViewPanesImpl> {
    val channel = Channel<ProjectViewPanesImpl>(Channel.CONFLATED)
    panesLoopMutex.withLock {
      panesLoop?.cancelAndJoin()
      panesLoop = coroutineScope.launch(CoroutineName("PV panes from the frontend")) {
        for (receivedPanes in channel) {
          panes.value = receivedPanes
        }
      }
    }
    return channel
  }
}

private class SelectPaneRequest(val id: Long, val paneId: ProjectViewPaneId) {
  val result = CompletableDeferred<Boolean>()

  fun toDTO(): SelectPaneRequestDTO = SelectPaneRequestDTO(id, paneId)

  override fun toString(): String = "SelectPaneRequest(id=$id, paneId=$paneId)"
}

private val LOG = logger<BackendProjectViewImpl>()
