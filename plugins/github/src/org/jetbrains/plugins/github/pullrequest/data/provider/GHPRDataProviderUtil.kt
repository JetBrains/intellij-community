// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.data.provider

import com.intellij.collaboration.async.awaitCompleted
import com.intellij.openapi.util.Ref
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class LoaderWithMutableCache<T>(private val cs: CoroutineScope, private val loader: suspend () -> T) {
  private val request = MutableStateFlow(loadAsync())
  private var lastLoaded: Ref<T>? = null
  private val dataGuard = Mutex()
  val updatedSignal: Flow<Unit> = request.drop(1).map { }

  suspend fun load(): T = request.awaitCompleted()

  suspend fun clearCache() {
    dataGuard.withLock {
      doClearCache()
    }
  }

  private fun doClearCache() {
    lastLoaded = null
    request.getAndUpdate { loadAsync() }.cancel()
  }

  suspend fun updateLoaded(updater: (T) -> T) {
    dataGuard.withLock {
      doUpdateLoaded(updater)
    }
  }

  private fun doUpdateLoaded(updater: (T) -> T) {
    val loaded = lastLoaded
    if (loaded != null) {
      val updated = updater(loaded.get())
      lastLoaded = Ref.create(updated)
      request.update { CompletableDeferred(updated) }
    }
  }

  private fun loadAsync(): Deferred<T> = cs.async(start = CoroutineStart.LAZY) {
    loader().also {
      dataGuard.withLock {
        lastLoaded = Ref.create(it)
      }
    }
  }
}