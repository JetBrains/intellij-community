// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab

import com.intellij.openapi.components.serviceAsync
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jetbrains.plugins.gitlab.api.*
import org.jetbrains.plugins.gitlab.api.dto.GitLabServerMetadataDTO
import org.jetbrains.plugins.gitlab.api.request.checkIsGitLabServer
import org.jetbrains.plugins.gitlab.api.request.getServerMetadata
import org.jetbrains.plugins.gitlab.api.request.getServerVersion
import org.jetbrains.plugins.gitlab.api.request.guessServerEdition
import org.jetbrains.plugins.gitlab.util.GitLabStatistics.logServerMetadataFetched
import java.util.concurrent.ConcurrentHashMap

interface GitLabServersManager {
  val earliestSupportedVersion: GitLabVersion

  /**
   * Does some guess to whether the given server path hosts a GitLab server.
   *
   * `null` means no guess has been completed yet.
   */
  suspend fun checkIsGitLabServer(server: GitLabServerPath): Boolean

  /**
   * Retrieves metadata for the given server using the given metadata fetching method.
   * If a cached metadata can be found, it can be used.
   *
   * This means that when a GitLab server is updated while the IDE is running, we might not be
   * able to detect that a new server version is deployed. If the cache is timed, we might detect
   * a new version every so often. If no cache is used, we will request metadata from the server
   * every time, but we know the server version to be up to date.
   *
   * @throws java.net.ConnectException when there is no usable internet connection.
   * @throws com.intellij.collaboration.api.HttpStatusErrorException when the API request results
   * in a non-successful status code.
   */
  suspend fun getMetadata(
    api: GitLabApi
  ): GitLabServerMetadata
}

internal class CachingGitLabServersManager(private val serviceCs: CoroutineScope) : GitLabServersManager {
  /** Cache of tests whether a given server path is a GitLab server path. */
  private val testCache = ConcurrentHashMap<GitLabServerPath, Deferred<Boolean>>()

  // Can't use map of Deferred bc loaders can be different, since metadata acquisition requires auth
  private val metadataCache = ConcurrentHashMap<GitLabServerPath, GitLabServerMetadata>()
  private val metadataCacheGuard = Mutex()

  override val earliestSupportedVersion: GitLabVersion = GitLabVersion(14, 0)

  override suspend fun checkIsGitLabServer(server: GitLabServerPath): Boolean =
    testCache.getOrPut(server) {
      serviceCs.async(Dispatchers.IO + CoroutineName("GitLab Server Tester")) {
        serviceAsync<GitLabApiManager>().getUnauthenticatedClient(server).rest.checkIsGitLabServer()
      }
    }.await()

  override suspend fun getMetadata(api: GitLabApi): GitLabServerMetadata =
    withContext(Dispatchers.IO + CoroutineName("GitLab Server Tester")) {
      metadataCacheGuard.withLock {
        val existing = metadataCache[api.server]
        if (existing != null) return@withLock existing

        val result = getServerMetadata(api)

        metadataCache[api.server] = result
        result
      }
    }
}

/**
 * Note that the endpoints used and called by this function are authenticated. The GitLabApi must
 * thus be created with authentication.
 *
 * @return `null` only when the server cannot be verified to be a GitLab server or no authentication
 * is provided. A valid [GitLabServerMetadata] object otherwise.
 */
// Unauthenticated
@SinceGitLab("8.13", note = "Enterprise/Community only detectable after 15.6, community is assumed by default")
private suspend fun getServerMetadata(api: GitLabApi): GitLabServerMetadata {
  val dto =
    try {
      // More recent. If it fails, use getServerVersion
      api.graphQL.getServerMetadata().body()
    }
    catch (e: CancellationException) {
      throw e
    }
    catch (_: Throwable) {
      val serverVersion = api.rest.getServerVersion().body()
      GitLabServerMetadataDTO(serverVersion.version, serverVersion.revision, null)
    } ?: throw IllegalStateException("Cannot fetch any metadata for server: ${api.server}")

  // Parse version
  val version = GitLabVersion.fromString(dto.version)

  // Try to guess enterprise/community if no explicit edition is provided. If all else fails, guess 'Community'
  val edition = when (dto.enterprise) {
    true -> GitLabEdition.Enterprise
    false -> GitLabEdition.Community
    else -> {
      api.rest.guessServerEdition() ?: GitLabEdition.Community
    }
  }

  val metadata = GitLabServerMetadata(version, dto.revision, edition)
  logServerMetadataFetched(metadata)
  return metadata
}