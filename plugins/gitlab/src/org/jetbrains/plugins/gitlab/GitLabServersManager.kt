// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab

import kotlinx.coroutines.*
import org.jetbrains.plugins.gitlab.api.GitLabApiImpl
import org.jetbrains.plugins.gitlab.api.GitLabServerPath
import org.jetbrains.plugins.gitlab.api.request.checkIsGitLabServer
import java.util.concurrent.ConcurrentHashMap

interface GitLabServersManager {
  suspend fun checkIsGitLabServer(server: GitLabServerPath): Boolean
}

internal class CachingGitLabServersManager(private val cs: CoroutineScope) : GitLabServersManager {

  private val cache = ConcurrentHashMap<GitLabServerPath, Deferred<Boolean>>()

  override suspend fun checkIsGitLabServer(server: GitLabServerPath): Boolean =
    cache.getOrPut(server) {
      cs.async(Dispatchers.IO + CoroutineName("GitLab Server metadata loader")) {
        GitLabApiImpl().checkIsGitLabServer(server)
      }
    }.await()
}