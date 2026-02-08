package com.intellij.grazie.cloud

import ai.grazie.client.common.SuspendableHTTPClient
import ai.grazie.client.common.model.HTTPResponse
import ai.grazie.client.common.model.Multipart
import ai.grazie.client.common.model.RequestOptions
import ai.grazie.model.cloud.exceptions.HTTPConnectionError
import ai.grazie.model.cloud.sse.ServerSentEvent
import kotlinx.coroutines.flow.Flow
import java.io.IOException
import java.nio.channels.UnresolvedAddressException

internal class GrazieHttpClientAdaptor(private val delegate: SuspendableHTTPClient): SuspendableHTTPClient {
  override suspend fun get(url: String, options: RequestOptions): HTTPResponse {
    return handleJdkExceptions {
      delegate.get(url, options)
    }
  }

  override suspend fun receiveContinuousSSE(
    url: String,
    content: String,
    options: RequestOptions,
    reconnect: Boolean
  ): Flow<ServerSentEvent> {
    return handleJdkExceptions {
      delegate.receiveContinuousSSE(url, content, options, reconnect)
    }
  }

  override suspend fun receiveContinuousSSE(
    url: String,
    content: ByteArray,
    options: RequestOptions,
    reconnect: Boolean,
  ): Flow<ServerSentEvent> {
    return handleJdkExceptions {
      delegate.receiveContinuousSSE(url, content, options, reconnect)
    }
  }

  override suspend fun receiveLineStream(
    url: String,
    content: String,
    options: RequestOptions,
    isLastLine: (String) -> Boolean
  ): Flow<String> {
    return handleJdkExceptions {
      delegate.receiveLineStream(url, content, options, isLastLine)
    }
  }

  override suspend fun send(url: String, content: Multipart, options: RequestOptions): HTTPResponse {
    return handleJdkExceptions {
      delegate.send(url, content, options)
    }
  }

  override suspend fun send(url: String, options: RequestOptions): HTTPResponse {
    return handleJdkExceptions {
      delegate.send(url, options)
    }
  }

  override suspend fun send(url: String, content: ByteArray, options: RequestOptions): HTTPResponse {
    return handleJdkExceptions {
      delegate.send(url, content, options)
    }
  }

  // Those exceptions should be handled by Ktor or at least Grazie client,
  // however neither of them does that for some reason
  private suspend fun <R> handleJdkExceptions(block: suspend () -> R): R {
    try {
      return block()
    } catch (exception: IOException) {
      throw HTTPConnectionError(description = exception.message, e = exception)
    } catch (exception: UnresolvedAddressException) {
      throw HTTPConnectionError(description = exception.message, e = exception)
    }
  }
}
