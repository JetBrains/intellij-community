// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor.impl.skeleton.rendering

import com.intellij.diagnostic.rethrowControlFlowException
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.logger
import com.intellij.util.ConcurrencyUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

/**
 * The thread that renders the frames of each registered [EditorSkeletonWindowRenderer].
 *
 * A frame does not wait for the EDT or for [kotlinx.coroutines.Dispatchers.Default].
 * The thread does not wake up while no renderer is registered.
 */
@Service(Service.Level.APP)
internal class EditorSkeletonRenderThread(private val coroutineScope: CoroutineScope) {
  private val dispatcher = Executors.newSingleThreadScheduledExecutor(
    ConcurrencyUtil.newNamedThreadFactory("Editor Skeleton Renderer", true, Thread.NORM_PRIORITY),
  ).asCoroutineDispatcher()
  private val renderers = MutableStateFlow<Set<EditorSkeletonWindowRenderer>>(emptySet())

  init {
    coroutineScope.coroutineContext.job.invokeOnCompletion { dispatcher.close() }
    coroutineScope.launch(dispatcher) {
      renderLoop()
    }
  }

  /**
   * Makes the thread render the frames of [renderer]. A second call for the same [renderer] does nothing.
   */
  fun register(renderer: EditorSkeletonWindowRenderer) {
    if (renderer !in renderers.value) {
      renderers.update { it + renderer }
    }
  }

  /**
   * Stops the frames of [renderer]. A frame in progress can still end after this call.
   */
  fun unregister(renderer: EditorSkeletonWindowRenderer) {
    val previous = renderers.getAndUpdate { it - renderer }
    if (renderer !in previous) {
      return
    }
    coroutineScope.launch(dispatcher) {
      if (renderer !in renderers.value) {
        renderer.releaseBackBuffer()
      }
    }
  }

  private suspend fun renderLoop() {
    while (true) {
      val active = renderers.value
      if (active.isEmpty()) {
        renderers.first { it.isNotEmpty() }
        continue
      }
      for (renderer in active) {
        renderSafely(renderer)
      }
      delay(EditorSkeletonRenderer.TICK_MS)
    }
  }

  private fun renderSafely(renderer: EditorSkeletonWindowRenderer) {
    try {
      renderer.renderFrame()
    }
    catch (e: Throwable) {
      rethrowControlFlowException(e)
      if (renderer.renderFailed()) {
        LOG.warn("The editor skeleton cannot render into the window. The skeleton stops its frames off the EDT.", e)
      }
    }
  }
}

private val LOG = logger<EditorSkeletonRenderThread>()
