// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent

import com.intellij.openapi.components.Service
import com.intellij.openapi.progress.blockingContext
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.future.asCompletableFuture
import java.util.concurrent.Callable
import java.util.concurrent.CompletableFuture

internal interface PersistentFSLoaderExecutor {
  fun <T> async(task: Callable<T>): CompletableFuture<T>?
}

@Service
internal class PersistentFsConnectorHelper(private val coroutineScope: CoroutineScope) {
  //FIXME RC: temporary, 'false' is not really a long-term alternative -- decide shortly about it
  private val PARALLELIZE_VFS_INITIALIZATION = System.getProperty("vfs.parallelize-initialization", "true").toBoolean()

  @JvmField
  val executor: PersistentFSLoaderExecutor = if (PARALLELIZE_VFS_INITIALIZATION) {
    object : PersistentFSLoaderExecutor {
      private val dispatcherAndName = CoroutineName("PersistentFsLoader") + Dispatchers.IO

      override fun <T> async(task: Callable<T>): CompletableFuture<T> {
        return coroutineScope.async(dispatcherAndName) {
          blockingContext {
            task.call()
          }
        }.asCompletableFuture()
      }
    }
  }
  else {
    object : PersistentFSLoaderExecutor {
      override fun <T> async(task: Callable<T>): CompletableFuture<T> {
        try {
          return CompletableFuture.completedFuture(task.call())
        }
        catch (e: Exception) {
          return CompletableFuture.failedFuture(e)
        }
      }
    }
  }
}