// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.progress.blockingContext
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.util.io.awaitFor
import kotlinx.coroutines.*
import kotlinx.coroutines.future.asCompletableFuture
import java.util.concurrent.CompletableFuture
import java.util.function.Function
import java.util.function.Supplier

@Service
internal class RefreshWorkerHelper(private val coroutineScope: CoroutineScope) {
  @JvmField
  val parallelism: Int = Registry.intValue("vfs.refresh.worker.parallelism", 4).coerceIn(1, Runtime.getRuntime().availableProcessors())

  @OptIn(ExperimentalCoroutinesApi::class)
  fun <T> createRefreshWorkerExecutor(): Function<Supplier<T>, CompletableFuture<T>> {
    val dispatcher = Dispatchers.IO.limitedParallelism(parallelism) + CoroutineName("VFS Refresh")
    @Suppress("DEPRECATION")
    ApplicationManager.getApplication().coroutineScope
    return Function {
      coroutineScope.async(dispatcher) {
        blockingContext {
          it.get()
        }
      }.asCompletableFuture()
    }
  }
}

suspend fun refreshVFSAsync() {
  val sessionId = blockingContext {
    VirtualFileManager.getInstance().asyncRefresh()
  }
  val refreshQueueImpl = RefreshQueue.getInstance() as? RefreshQueueImpl
  val session = refreshQueueImpl?.getSession(sessionId) ?: return
  try {
    session.semaphore.awaitFor()
  }
  catch (t: Throwable) {
    refreshQueueImpl.cancelSession(sessionId)
    throw t
  }
}
