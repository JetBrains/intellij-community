// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.frontend.util

import com.intellij.openapi.diagnostic.thisLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.completeWith
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope

/**
 * A utility class designed to serialize and manage the execution of asynchronous RPC requests.
 * Each request is scheduled and executed sequentially to ensure thread-safe operation.
 */
internal class SequentialRpcRequestsExecutor private constructor() {
  private val requests = Channel<Request>(Channel.UNLIMITED, onUndeliveredElement = { request ->
    request.markUndelivered()
  })

  fun <T> submit(block: suspend () -> T): Deferred<T> {
    val completableRequest = CompletableRequest(block)
    requests.trySend(completableRequest)
    return completableRequest.result
  }

  fun execute(block: suspend () -> Unit) {
    val simpleRequest = SimpleRequest(block)
    requests.trySend(simpleRequest)
  }

  private sealed interface Request {
    suspend fun performRequest()
    fun markUndelivered() {}
  }

  private class SimpleRequest(val request: suspend () -> Unit) : Request {
    override suspend fun performRequest() {
      try {
        request()
      }
      catch (e: Throwable) {
        if (e !is CancellationException) {
          thisLogger().error("Error during request execution", e)
        }
      }
    }
  }

  private class CompletableRequest<T>(val request: suspend () -> T) : Request {
    val result = CompletableDeferred<T>()

    override suspend fun performRequest() {
      if (!result.isActive) return
      try {
        supervisorScope {
          val requestResult = async { request() }
          result.invokeOnCompletion { requestResult.cancel() }
          result.completeWith(runCatching { requestResult.await() })
        }
      }
      finally {
        result.cancel()
      }

    }

    override fun markUndelivered() {
      result.cancel()
    }
  }

  companion object {
    fun create(cs: CoroutineScope): SequentialRpcRequestsExecutor {
      val executor = SequentialRpcRequestsExecutor()
      cs.launch {
        executor.requests.consumeEach { request ->
          request.performRequest()
        }
      }
      return executor
    }
  }
}
