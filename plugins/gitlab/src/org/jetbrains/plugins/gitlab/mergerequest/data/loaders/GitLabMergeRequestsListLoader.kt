// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.data.loaders

import com.intellij.collaboration.api.page.SequentialListLoader
import com.intellij.collaboration.api.page.SequentialListLoader.ListBatch
import com.intellij.collaboration.api.util.LinkHttpHeaderValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.jetbrains.plugins.gitlab.api.GitLabApi
import org.jetbrains.plugins.gitlab.api.GitLabProjectCoordinates
import org.jetbrains.plugins.gitlab.api.loadList
import org.jetbrains.plugins.gitlab.mergerequest.api.request.loadMergeRequests
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabMergeRequestDetails
import org.jetbrains.plugins.gitlab.util.GitLabApiRequestName

internal class GitLabMergeRequestsListLoader(
  private val api: GitLabApi,
  private val project: GitLabProjectCoordinates,
  private val searchQuery: String
) : SequentialListLoader<GitLabMergeRequestDetails> {

  private val loadingMutex = Mutex()

  @Volatile
  private var nextRequest: (suspend () -> Pair<List<GitLabMergeRequestDetails>, String?>)?

  init {
    nextRequest = {
      loadMergeRequests(null)
    }
  }

  private suspend fun loadMergeRequests(nextUri: String?): Pair<List<GitLabMergeRequestDetails>, String?> {
    val response = if (nextUri == null) {
      api.rest.loadMergeRequests(project, searchQuery)
    }
    else {
      api.rest.loadList(GitLabApiRequestName.REST_GET_MERGE_REQUESTS, nextUri)
    }
    val linkHeader = response.headers().firstValue(LinkHttpHeaderValue.HEADER_NAME).orElse(null)?.let(LinkHttpHeaderValue::parse)
    val result = response.body().map(GitLabMergeRequestDetails.Companion::fromRestDTO)
    return result to linkHeader?.nextLink
  }

  override suspend fun loadNext(): ListBatch<GitLabMergeRequestDetails> =
    withContext(Dispatchers.IO) {
      doLoad()
    }

  private suspend fun doLoad(): ListBatch<GitLabMergeRequestDetails> {
    loadingMutex.withLock {
      val request = nextRequest
      if (request == null) {
        return ListBatch(emptyList(), false)
      }
      else {
        val (data, nextLink) = request()
        nextRequest = nextLink?.let {
          {
            loadMergeRequests(nextLink)
          }
        }
        return ListBatch(data, nextLink != null)
      }
    }
  }
}