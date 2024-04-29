// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("AppJavaExecutorUtil")
@file:OptIn(ExperimentalCoroutinesApi::class)

package com.intellij.util.concurrency

import com.intellij.codeWithMe.ClientId
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.ComponentManagerEx
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.blockingContext
import com.intellij.platform.util.coroutines.namedChildScope
import kotlinx.coroutines.*
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.TestOnly
import java.util.concurrent.TimeUnit

/**
 * Only for Java clients and only if you cannot rewrite in Kotlin and use coroutines (as you should).
 */
@Internal
fun executeOnPooledIoThread(task: Runnable) {
  (ApplicationManager.getApplication() as ComponentManagerEx).getCoroutineScope().launch(Dispatchers.IO) {
    blockingContext(task::run)
  }
}

@Internal
fun createSingleTaskApplicationPoolExecutor(name: String, coroutineScope: CoroutineScope): CoroutineDispatcherBackedExecutor {
  return CoroutineDispatcherBackedExecutor(coroutineScope, name)
}

// TODO expose interface if ever goes public
@Internal
@OptIn(ExperimentalCoroutinesApi::class)
class CoroutineDispatcherBackedExecutor(coroutineScope: CoroutineScope, name: String) {
  private val childScope = coroutineScope.namedChildScope(name, Dispatchers.IO.limitedParallelism(parallelism = 1))

  fun isEmpty(): Boolean = childScope.coroutineContext.job.children.none()

  fun schedule(it: Runnable) {
    childScope.launch(ClientId.coroutineContext()) {
      blockingContext {
        it.run()
      }
    }
  }

  fun cancel() {
    childScope.cancel()
  }

  @TestOnly
  fun waitAllTasksExecuted(timeout: Long, timeUnit: TimeUnit) {
    @Suppress("RAW_RUN_BLOCKING")
    runBlocking {
      withTimeout(timeUnit.toMillis(timeout)) {
        while (true) {
          val jobs = childScope.coroutineContext.job.children.toList()
          if (jobs.isEmpty()) {
            break
          }
          jobs.joinAll()
        }
      }
    }
  }
}
