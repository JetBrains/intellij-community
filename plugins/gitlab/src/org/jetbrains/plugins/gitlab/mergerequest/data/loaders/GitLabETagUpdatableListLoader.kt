// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.data.loaders

import com.intellij.collaboration.api.page.ApiPageUtil
import com.intellij.collaboration.util.ResultUtil.runCatchingUser
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import java.net.URI
import java.net.http.HttpResponse

class GitLabETagUpdatableListLoader<T>(
  private val initialURI: URI,
  private val updateRequests: Flow<Unit>,
  private val request: suspend (uri: URI, eTag: String?) -> HttpResponse<out List<T>?>,
) {
  val events: Flow<Result<List<T>>> =
      channelFlow {
        val allEvents = mutableListOf<T>()
        runCatchingUser {
          var lastETag: String? = null
          ApiPageUtil.createPagesFlowByLinkHeader(initialURI) {
            request(it, null)
          }.collect {
            allEvents += it.body() ?: error("Empty response")
            send(Result.success(allEvents))
            lastETag = it.headers().firstValue("ETag").orElse(null)
          }

          updateRequests.collect {
            val response = request(initialURI, lastETag)
            val result = response.body()
            result?.let {
              allEvents += it
              send(Result.success(allEvents))
            }
            lastETag = response.headers().firstValue("ETag").orElse(null)
            if (lastETag == null) {
              currentCoroutineContext().cancel()
            }
          }
        }.onFailure { send(Result.failure(it)) }
      }
}