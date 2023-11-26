// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("AppJavaExecutorUtil")
@file:OptIn(ExperimentalCoroutinesApi::class)
package com.intellij.util.concurrency

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.blockingContext
import com.intellij.platform.util.coroutines.childScope
import kotlinx.coroutines.*
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.TestOnly
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit
import kotlin.coroutines.CoroutineContext

/**
 * Only for Java clients and only if you cannot rewrite in Kotlin and use coroutines (as you should).
 */
@Internal
fun executeOnPooledIoThread(task: Runnable) {
  @Suppress("DEPRECATION")
  ApplicationManager.getApplication().coroutineScope.launch(Dispatchers.IO) {
    blockingContext(task::run)
  }
}

@Internal
fun createSingleTaskApplicationPoolExecutor(name: String, coroutineScope: CoroutineScope): Executor {
  return CoroutineDispatcherBackedExecutor(coroutineScope, context = Dispatchers.IO.limitedParallelism(1) + CoroutineName(name))
}

@Internal
class CoroutineDispatcherBackedExecutor(coroutineScope: CoroutineScope, private val context: CoroutineContext) : Executor {
  private val childScope = coroutineScope.childScope()

  fun isEmpty(): Boolean = childScope.coroutineContext.job.children.none()

  override fun execute(it: Runnable) {
    childScope.launch(context) {
      // `blockingContext` is not used by intention - low-level tasks are expected in such executors
      try {
        it.run()
      }
      catch (_: ProcessCanceledException) { }
    }
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
