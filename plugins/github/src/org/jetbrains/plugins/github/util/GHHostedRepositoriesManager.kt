// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.util

import com.intellij.collaboration.async.disposingScope
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import git4idea.remote.GitRemoteUrlCoordinates
import git4idea.remote.hosting.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.future.await
import org.jetbrains.annotations.VisibleForTesting
import org.jetbrains.plugins.github.api.GithubServerPath
import org.jetbrains.plugins.github.authentication.accounts.GHAccountManager

@Service
class GHHostedRepositoriesManager(project: Project) : HostedGitRepositoriesManager<GHGitRepositoryMapping>, Disposable {

  override val knownRepositoriesState: StateFlow<Set<GHGitRepositoryMapping>> by lazy {
    knownRepositoriesFlow.stateIn(disposingScope(), SharingStarted.Eagerly, emptySet())
  }

  @VisibleForTesting
  internal val knownRepositoriesFlow = run {
    val gitRemotesFlow = gitRemotesFlow(project).distinctUntilChanged()

    val accountsServersFlow = service<GHAccountManager>().accountsState.map { accounts ->
      mutableSetOf(GithubServerPath.DEFAULT_SERVER) + accounts.map { it.server }
    }.distinctUntilChanged()

    val discoveredServersFlow = gitRemotesFlow.discoverServers(accountsServersFlow) {
      checkForDedicatedServer(it)
    }.runningFold(emptySet<GithubServerPath>()) { accumulator, value ->
      accumulator + value
    }.distinctUntilChanged()

    val serversFlow = accountsServersFlow.combine(discoveredServersFlow) { servers1, servers2 ->
      servers1 + servers2
    }

    gitRemotesFlow.mapToServers(serversFlow) { server, remote ->
      GHGitRepositoryMapping.create(server, remote)
    }.onEach {
      LOG.debug("New list of known repos: $it")
    }
  }

  private suspend fun checkForDedicatedServer(remote: GitRemoteUrlCoordinates): GithubServerPath? {
    val uri = GitHostingUrlUtil.getUriFromRemoteUrl(remote.url)
    LOG.debug("Extracted URI $uri from remote ${remote.url}")
    if (uri == null) return null

    val host = uri.host ?: return null
    val path = uri.path ?: return null
    val pathParts = path.removePrefix("/").split('/').takeIf { it.size >= 2 } ?: return null
    val serverSuffix = if (pathParts.size == 2) null else pathParts.subList(0, pathParts.size - 2).joinToString("/", "/")

    for (server in listOf(
      GithubServerPath(false, host, null, serverSuffix),
      GithubServerPath(true, host, null, serverSuffix),
      GithubServerPath(true, host, 8080, serverSuffix)
    )) {
      LOG.debug("Looking for GHE server at $server")
      try {
        service<GHEnterpriseServerMetadataLoader>().loadMetadata(server).await()
        LOG.debug("Found GHE server at $server")
        return server
      }
      catch (ignored: Throwable) {
      }
    }
    return null
  }

  override fun dispose() = Unit

  companion object {
    private val LOG = logger<GHHostedRepositoriesManager>()
  }
}