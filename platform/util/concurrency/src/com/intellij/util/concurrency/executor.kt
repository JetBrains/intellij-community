// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("AppJavaExecutorUtil")
@file:OptIn(ExperimentalCoroutinesApi::class)

package com.intellij.util.concurrency

import com.intellij.codeWithMe.ClientId
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.ComponentManagerEx
import com.intellij.openapi.progress.runBlockingMaybeCancellable
import com.intellij.openapi.util.Disposer
import com.intellij.platform.util.coroutines.childScope
import com.intellij.util.awaitCancellationAndInvoke
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
fun executeOnPooledIoThread(task: Runnable): Job {
  return (ApplicationManager.getApplication() as ComponentManagerEx).getCoroutineScope().launch(Dispatchers.IO) {
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

/**
 * Only for Java clients and only if you cannot rewrite in Kotlin and use coroutines (as you should).
 */
@Internal
@ApiStatus.Obsolete
fun executeOnPooledCpuThread(coroutineScope: CoroutineScope, task: Runnable) {
  coroutineScope.launch {
    task.run()
  }
}

/**
 * Only for Java clients and only if you cannot rewrite in Kotlin and use coroutines (as you should).
 */
@Internal
@ApiStatus.Obsolete
fun CoroutineScope.awaitCancellationAndDispose(disposable: Disposable) {
  awaitCancellationAndInvoke {
    Disposer.dispose(disposable)
  }
}

@Internal
@JvmOverloads
fun createBoundedTaskExecutor(
  name: String,
  coroutineScope: CoroutineScope,
  concurrency: Int = 1,
): CoroutineDispatcherBackedExecutor {
  return CoroutineDispatcherBackedExecutor(coroutineScope = coroutineScope, name = name, concurrency = concurrency)
}

// TODO expose interface if ever goes public
@Internal
@OptIn(ExperimentalCoroutinesApi::class)
class CoroutineDispatcherBackedExecutor(coroutineScope: CoroutineScope, name: String, concurrency: Int) : Executor {
  private val childScope = coroutineScope.childScope(name, Dispatchers.IO.limitedParallelism(parallelism = concurrency))

  fun isEmpty(): Boolean = childScope.coroutineContext.job.children.none()

  override fun execute(command: Runnable) {
    childScope.coroutineContext.ensureActive()
    executeSuspending { command.run() }
  }

  fun <T> executeSuspending(action: suspend () -> T): Deferred<T> {
    return childScope.async(ClientId.coroutineContext()) {
      action()
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
    waitAllTasksExecuted(coroutineScope = childScope, timeout = timeout, timeUnit = timeUnit)
  }
}

@Internal
@TestOnly
fun waitAllTasksExecuted(coroutineScope: CoroutineScope, timeout: Long, timeUnit: TimeUnit) {
  @Suppress("RAW_RUN_BLOCKING")
  runBlocking {
    withTimeout(timeUnit.toMillis(timeout)) {
      while (true) {
        val jobs = coroutineScope.coroutineContext.job.children.toList()
        if (jobs.isEmpty()) {
          break
        }
        jobs.joinAll()
      }
    }
  }
}
