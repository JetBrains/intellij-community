// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.blockingContext
import com.intellij.util.concurrency.AppExecutorUtil
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.future.asCompletableFuture
import java.util.concurrent.Callable
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService

/** Abstracts out specific way to run async tasks during VFS initialization */
interface VFSAsyncTaskExecutor {
  fun <T> async(task: Callable<T>): CompletableFuture<T>
}

@Service
internal class ExecuteOnCoroutine(private val coroutineScope: CoroutineScope) : VFSAsyncTaskExecutor {
  private val dispatcherAndName = CoroutineName("PersistentFsLoader") + Dispatchers.IO
  override fun <T> async(task: Callable<T>): CompletableFuture<T> {
    return coroutineScope.async(dispatcherAndName) {
      blockingContext {
        task.call()
      }
    }.asCompletableFuture()
  }
}

class ExecuteOnThreadPool(private val pool: ExecutorService) : VFSAsyncTaskExecutor {
  override fun <T> async(task: Callable<T>): CompletableFuture<T> {
    //MAYBE RC: use Dispatchers.IO-kind pool, with many threads (appExecutor has 1 core thread, so needs time to inflate)
    return CompletableFuture.supplyAsync(
      { task.call() },
      pool
    )
  }
}

internal object ExecuteOnCallingThread : VFSAsyncTaskExecutor {
  override fun <T> async(task: Callable<T>): CompletableFuture<T> {
    try {
      return CompletableFuture.completedFuture(task.call())
    }
    catch (e: Exception) {
      return CompletableFuture.failedFuture(e)
    }
  }
}

internal object PersistentFsConnectorHelper {
  private val PARALLELIZE_VFS_INITIALIZATION = System.getProperty("vfs.parallelize-initialization", "true").toBoolean()
  private val USE_COROUTINES_DISPATCHER = System.getProperty("vfs.use-coroutines-dispatcher", "true").toBoolean()

  fun executor(): VFSAsyncTaskExecutor {
    if (!PARALLELIZE_VFS_INITIALIZATION) {
      return ExecuteOnCallingThread
    }
    else if (USE_COROUTINES_DISPATCHER) {
      val app = ApplicationManager.getApplication()
      if (app != null) {
        return app.service<ExecuteOnCoroutine>()
      }
      else {
        return ExecuteOnCoroutine(coroutineScope = CoroutineScope(Dispatchers.IO))
      }
    }
    else {
      val pool = AppExecutorUtil.getAppExecutorService()
      return ExecuteOnThreadPool(pool)
    }
  }
}