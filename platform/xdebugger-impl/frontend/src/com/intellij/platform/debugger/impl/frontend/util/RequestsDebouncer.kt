// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.frontend.util

import kotlinx.coroutines.Deferred
import java.util.concurrent.ConcurrentHashMap

internal class RequestsDebouncer<K : Any>(
  private val sequentialExecutor: SequentialRpcRequestsExecutor,
) {
  private val currentRequests = ConcurrentHashMap<K, Deferred<Unit>>()

  fun submit(key: K, request: suspend () -> Unit): Deferred<Unit> {
    val newRequest = sequentialExecutor.submit(request)
    val oldRequest = currentRequests.put(key, newRequest)
    oldRequest?.cancel()
    newRequest.invokeOnCompletion {
      currentRequests.remove(key, newRequest)
    }
    return newRequest
  }
}
