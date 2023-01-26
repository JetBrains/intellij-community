// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.data

import com.intellij.util.childScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withContext
import org.jetbrains.plugins.gitlab.api.GitLabApi
import org.jetbrains.plugins.gitlab.api.GitLabProjectCoordinates
import org.jetbrains.plugins.gitlab.mergerequest.api.request.loadMergeRequest
import java.util.concurrent.ConcurrentHashMap

interface GitLabProjectMergeRequestsStore {
  /**
   * @return a handle for result of loading a shared MR model
   */
  fun getShared(id: GitLabMergeRequestId): SharedFlow<Result<GitLabMergeRequest>>
}

class CachingGitLabProjectMergeRequestsStore(parentCs: CoroutineScope,
                                             private val api: GitLabApi,
                                             private val project: GitLabProjectCoordinates) : GitLabProjectMergeRequestsStore {

  private val cs = parentCs.childScope()

  private val cache = ConcurrentHashMap<GitLabMergeRequestId, SharedFlow<Result<GitLabMergeRequest>>>()

  override fun getShared(id: GitLabMergeRequestId): SharedFlow<Result<GitLabMergeRequest>> {
    val simpleId = GitLabMergeRequestId.Simple(id)
    return cache.getOrPut(simpleId) {
      channelFlow {
        val result = runCatching {
          val mrData = withContext(Dispatchers.IO) {
            api.loadMergeRequest(project, id).body()!!
          }
          LoadedGitLabMergeRequest(this, api, project, mrData)
        }
        send(result)
        awaitClose()
      }.onCompletion { cache.remove(simpleId) }
        .shareIn(cs, SharingStarted.WhileSubscribed(0, 0), 1)
      // this the model will only be alive while it's needed
    }
  }
}