// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.util

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import git4idea.remote.hosting.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*
import org.jetbrains.annotations.VisibleForTesting
import org.jetbrains.plugins.github.api.GithubServerPath
import org.jetbrains.plugins.github.authentication.accounts.GHAccountManager

@Service(Service.Level.PROJECT)
class GHHostedRepositoriesManager(project: Project, cs: CoroutineScope) : HostedGitRepositoriesManager<GHGitRepositoryMapping> {

  @VisibleForTesting
  internal val knownRepositoriesFlow = run {
    val gitRemotesFlow = gitRemotesFlow(project).distinctUntilChanged()

    val accountsServersFlow = service<GHAccountManager>().accountsState.map { accounts ->
      mutableSetOf(GithubServerPath.DEFAULT_SERVER) + accounts.map { it.server }
    }.distinctUntilChanged()

    val discoveredServersFlow = gitRemotesFlow.discoverServers(accountsServersFlow) { remote ->
      GitHostingUrlUtil.findServerAt(LOG, remote) {
        val server = GithubServerPath.from(it.toString())
        if (server.isGheDataResidency) return@findServerAt server

        val metadata = runCatching { serviceAsync<GHEnterpriseServerMetadataLoader>().loadMetadata(server) }.getOrNull()
        if (metadata != null) server else null
      }
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

  override val knownRepositoriesState: StateFlow<Set<GHGitRepositoryMapping>> =
    knownRepositoriesFlow.stateIn(cs, getStateSharingStartConfig(), emptySet())

  companion object {
    private val LOG = logger<GHHostedRepositoriesManager>()

    private fun getStateSharingStartConfig() =
      if (ApplicationManager.getApplication().isUnitTestMode) SharingStarted.Eagerly else SharingStarted.Lazily
  }
}