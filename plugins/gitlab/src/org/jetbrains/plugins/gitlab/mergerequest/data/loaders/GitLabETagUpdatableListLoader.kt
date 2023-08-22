// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.data.loaders

import com.intellij.collaboration.api.page.ApiPageUtil
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.*
import java.net.URI
import java.net.http.HttpResponse

class GitLabETagUpdatableListLoader<T>(
  private val initialURI: URI,
  private val request: suspend (uri: URI, eTag: String?) -> HttpResponse<out List<T>?>
) {
  private val updateRequests = MutableSharedFlow<Unit>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

  val batches: Flow<List<T>> = channelFlow {
    var lastETag: String? = null
    ApiPageUtil.createPagesFlowByLinkHeader(initialURI) {
      request(it, null)
    }.collect {
      send(it.body() ?: error("Empty response"))
      lastETag = it.headers().firstValue("ETag").orElse(null)
    }

    updateRequests.collect {
      val response = request(initialURI, lastETag)
      val result = response.body()
      result?.let { send(it) }
      lastETag = response.headers().firstValue("ETag").orElse(null)
      if (lastETag == null) {
        currentCoroutineContext().cancel()
      }
    }
  }

  suspend fun checkForUpdates() {
    updateRequests.emit(Unit)
  }
}