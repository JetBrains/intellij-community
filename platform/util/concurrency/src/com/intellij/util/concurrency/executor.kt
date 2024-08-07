// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("AppJavaExecutorUtil")
@file:OptIn(ExperimentalCoroutinesApi::class)

package com.intellij.util.concurrency

import com.intellij.codeWithMe.ClientId
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.ComponentManagerEx
import com.intellij.openapi.progress.blockingContext
import com.intellij.openapi.progress.runBlockingMaybeCancellable
import com.intellij.platform.util.coroutines.childScope
import kotlinx.coroutines.*
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.TestOnly
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit

/**
 * Only for Java clients and only if you cannot rewrite in Kotlin and use coroutines (as you should).
 */
@Internal
@ApiStatus.Obsolete
fun executeOnPooledIoThread(task: Runnable) {
  (ApplicationManager.getApplication() as ComponentManagerEx).getCoroutineScope().launch(Dispatchers.IO) {
    task.run()
  }
}

/**
 * Only for Java clients and only if you cannot rewrite in Kotlin and use coroutines (as you should).
 */
@Internal
@ApiStatus.Obsolete
fun executeOnPooledIoThread(coroutineScope: CoroutineScope, task: Runnable) {
  coroutineScope.launch(Dispatchers.IO) {
    task.run()
  }
}

@Internal
@JvmOverloads
fun createBoundedTaskExecutor(
  name: String,
  coroutineScope: CoroutineScope,
  parallelism: Int = 1,
): CoroutineDispatcherBackedExecutor {
  return CoroutineDispatcherBackedExecutor(coroutineScope = coroutineScope, name = name, parallelism = parallelism)
}

// TODO expose interface if ever goes public
@Internal
@OptIn(ExperimentalCoroutinesApi::class)
class CoroutineDispatcherBackedExecutor(coroutineScope: CoroutineScope, name: String, parallelism: Int) : Executor {
  private val childScope = coroutineScope.childScope(name, Dispatchers.IO.limitedParallelism(parallelism = parallelism))

  fun isEmpty(): Boolean = childScope.coroutineContext.job.children.none()

  override fun execute(command: Runnable) {
    childScope.launch(ClientId.coroutineContext()) {
      blockingContext {
        command.run()
      }
    }
  }

  fun cancel() {
    for (job in childScope.coroutineContext.job.children.toList()) {
      job.cancel()
    }
  }

  @TestOnly
  fun cancelAndWaitAllTasksExecuted(timeout: Long, timeUnit: TimeUnit) {
    runBlockingMaybeCancellable {
      withTimeout(timeUnit.toMillis(timeout)) {
        while (true) {
          val jobs = childScope.coroutineContext.job.children.toList()
          if (jobs.isEmpty()) {
            break
          }

          for (job in jobs) {
            job.cancel()
          }
          jobs.joinAll()
        }
      }
    }
  }

  @Throws(TimeoutCancellationException::class)
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
