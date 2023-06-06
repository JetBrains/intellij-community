// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab

import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jetbrains.plugins.gitlab.api.GitLabApiImpl
import org.jetbrains.plugins.gitlab.api.GitLabServerPath
import org.jetbrains.plugins.gitlab.api.dto.GitLabServerMetadataDTO
import org.jetbrains.plugins.gitlab.api.request.checkIsGitLabServer
import java.util.concurrent.ConcurrentHashMap

interface GitLabServersManager {
  suspend fun checkIsGitLabServer(server: GitLabServerPath): Boolean

  suspend fun getMetadata(server: GitLabServerPath, loader: suspend (GitLabServerPath) -> Result<GitLabServerMetadataDTO>)
    : GitLabServerMetadataDTO?
}

internal class CachingGitLabServersManager(private val cs: CoroutineScope) : GitLabServersManager {

  private val testCache = ConcurrentHashMap<GitLabServerPath, Deferred<Boolean>>()

  // can't use map of Deferred bc loaders can be different, since metadata acquisition requires auth
  private val metadataCache = ConcurrentHashMap<GitLabServerPath, GitLabServerMetadataDTO>()
  private val metadataCacheGuard = Mutex()

  override suspend fun checkIsGitLabServer(server: GitLabServerPath): Boolean =
    testCache.getOrPut(server) {
      cs.async(Dispatchers.IO + CoroutineName("GitLab Server tester")) {
        GitLabApiImpl().rest.checkIsGitLabServer(server)
      }
    }.await()


  override suspend fun getMetadata(server: GitLabServerPath, loader: suspend (GitLabServerPath) -> Result<GitLabServerMetadataDTO>)
    : GitLabServerMetadataDTO? =
    metadataCacheGuard.withLock {
      val existing = metadataCache[server]
      if (existing != null) return@withLock existing

      val result = loader(server).getOrNull()
      if (result != null) {
        metadataCache[server] = result
      }
      result
    }
}