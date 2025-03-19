// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vfs

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.serviceIfCreated
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.containers.ContainerUtil
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import org.jetbrains.annotations.VisibleForTesting
import kotlin.coroutines.coroutineContext

private val LOG = logger<AsyncVfsEventsPostProcessorImpl>()

@ApiStatus.Internal
@VisibleForTesting
class AsyncVfsEventsPostProcessorImpl(coroutineScope: CoroutineScope) : AsyncVfsEventsPostProcessor {
  private val queue = MutableSharedFlow<Any>(extraBufferCapacity = Int.MAX_VALUE)
  private val messageBus = ApplicationManager.getApplication().messageBus

  private val listeners = ContainerUtil.createConcurrentList<Pair<AsyncVfsEventsListener, CoroutineScope>>()

  init {
    coroutineScope.launch {
      queue.collect { events ->
        if (events is Runnable) {
          events.run()
        }
        else {
          @Suppress("UNCHECKED_CAST")
          processEvents(events as List<VFileEvent>)
        }
      }
    }

    coroutineScope.coroutineContext.job.invokeOnCompletion {
      listeners.clear()
    }

    messageBus.connect(coroutineScope).subscribe(VirtualFileManager.VFS_CHANGES, object : BulkFileListener {
      override fun after(events: List<VFileEvent>) {
        check(queue.tryEmit(events))
      }
    })
  }

  override fun addListener(listener: AsyncVfsEventsListener, coroutineScope: CoroutineScope) {
    val element = listener to coroutineScope
    listeners.add(element)
    coroutineScope.coroutineContext.job.invokeOnCompletion {
      listeners.remove(element)
    }
  }

  @RequiresBackgroundThread
  private suspend fun processEvents(events: List<VFileEvent>) {
    for ((listener, listenerScope) in listeners) {
      coroutineContext.ensureActive()
      listenerScope.launch(Dispatchers.IO) {
        try {
          listener.filesChanged(events)
        }
        catch (e: CancellationException) {
          throw e
        }
        catch (e: Throwable) {
          LOG.error(e)
        }
      }.join()
    }
  }

  companion object {
    @Suppress("SSBasedInspection")
    @JvmStatic
    @TestOnly
    fun waitEventsProcessed() {
      assert(ApplicationManager.getApplication().isUnitTestMode)
      val processor = serviceIfCreated<AsyncVfsEventsPostProcessor>() ?: return
      runBlocking {
        val job = CompletableDeferred<Unit>(parent = coroutineContext.job)
        (processor as AsyncVfsEventsPostProcessorImpl).queue.tryEmit(Runnable {
          job.complete(Unit)
        })
        job.join()
      }
    }
  }
}
