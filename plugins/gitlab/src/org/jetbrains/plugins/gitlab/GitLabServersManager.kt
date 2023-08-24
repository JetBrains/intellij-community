// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab

import com.intellij.openapi.util.NlsSafe
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jetbrains.annotations.NonNls
import org.jetbrains.plugins.gitlab.api.GitLabApi
import org.jetbrains.plugins.gitlab.api.GitLabApiImpl
import org.jetbrains.plugins.gitlab.api.GitLabServerPath
import org.jetbrains.plugins.gitlab.api.dto.GitLabServerMetadataDTO
import org.jetbrains.plugins.gitlab.api.request.checkIsGitLabServer
import org.jetbrains.plugins.gitlab.api.request.getServerMetadataOrVersion
import org.jetbrains.plugins.gitlab.util.GitLabBundle
import java.util.concurrent.ConcurrentHashMap

interface GitLabServersManager {
  suspend fun checkIsGitLabServer(server: GitLabServerPath): Boolean

  suspend fun getMetadata(server: GitLabServerPath, loader: suspend (GitLabServerPath) -> Result<GitLabServerMetadataDTO>)
    : GitLabServerMetadataDTO?

  fun isVersionSupported(version: @NonNls String): Boolean

  fun getEarliestSupportedVersion(): @NlsSafe String
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

  override fun isVersionSupported(version: String): Boolean {
    val split = version.split('.')
    require(split.size >= 2) { GitLabBundle.message("server.version.error") }
    val major = split[0].toInt()
    val minor = split[1].toInt()

    if (major > EARLIEST_VERSION_MAJOR) return true
    if (major == EARLIEST_VERSION_MAJOR && minor >= EARLIEST_VERSION_MINOR) return true
    return false
  }

  override fun getEarliestSupportedVersion(): String = "$EARLIEST_VERSION_MAJOR.$EARLIEST_VERSION_MINOR"

  private companion object {
    const val EARLIEST_VERSION_MAJOR = 15
    const val EARLIEST_VERSION_MINOR = 10
  }
}

suspend fun GitLabServersManager.validateServerVersion(server: GitLabServerPath, api: GitLabApi) {
  val metadata = getMetadataCached(server, api)
  val versionSupported = isVersionSupported(metadata.version)
  require(versionSupported) {
    GitLabBundle.message("server.version.unsupported", metadata.version, getEarliestSupportedVersion())
  }
}

suspend fun GitLabServersManager.isServerVersionSupported(server: GitLabServerPath, api: GitLabApi): Boolean {
  val metadata = getMetadataCached(server, api)
  return isVersionSupported(metadata.version)
}

private suspend fun GitLabServersManager.getMetadataCached(server: GitLabServerPath, api: GitLabApi): GitLabServerMetadataDTO =
  withContext(Dispatchers.IO) {
    getMetadata(server) {
      runCatching {
        api.rest.getServerMetadataOrVersion(server)
      }
    }.let {
      requireNotNull(it) { GitLabBundle.message("server.version.error") }
    }
  }