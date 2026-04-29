// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.util

import com.intellij.collaboration.async.withInitial
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.options.advanced.AdvancedSettings
import com.intellij.openapi.options.advanced.AdvancedSettingsChangeListener
import com.intellij.openapi.project.Project
import git4idea.remote.hosting.GitHostingUrlUtil
import git4idea.remote.hosting.GitHostingUrlUtil.getUriFromRemoteUrl
import git4idea.remote.hosting.HostedGitRepositoriesManager
import git4idea.remote.hosting.discoverServers
import git4idea.remote.hosting.gitRemotesFlow
import git4idea.remote.hosting.mapToServers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.runningFold
import kotlinx.coroutines.flow.stateIn
import org.jetbrains.annotations.VisibleForTesting
import org.jetbrains.plugins.github.api.GithubServerPath
import org.jetbrains.plugins.github.authentication.accounts.GHAccountManager

@Service(Service.Level.PROJECT)
class GHHostedRepositoriesManager(project: Project, cs: CoroutineScope) : HostedGitRepositoriesManager<GHGitRepositoryMapping> {

  @ExperimentalCoroutinesApi
  @VisibleForTesting
  internal val knownRepositoriesFlow: Flow<Set<GHGitRepositoryMapping>> = createKnownRepositoriesFlow(project)


  private fun settingChangeEventsFlow(): Flow<Unit> = callbackFlow {
    val connection = ApplicationManager.getApplication().messageBus.connect(this)
    connection.subscribe(AdvancedSettingsChangeListener.TOPIC, object : AdvancedSettingsChangeListener {
      override fun advancedSettingChanged(id: String, oldValue: Any, newValue: Any) {
        if (id == GITHUB_ALIASES_SETTING_ID) trySend(Unit)
      }
    })
    awaitClose()
  }

  private fun createAliasesFlow(): Flow<Set<String>> = settingChangeEventsFlow().withInitial(Unit).map { readAliasesFromSettings() }

  private fun createKnownRepositoriesFlow(project: Project): Flow<Set<GHGitRepositoryMapping>> {
    val gitRemotesFlow = gitRemotesFlow(project).distinctUntilChanged()
    val defaultServerAliasesFlow = createAliasesFlow().distinctUntilChanged()

    val accountsServersFlow = service<GHAccountManager>().accountsState.map { accounts ->
      mutableSetOf(GithubServerPath.DEFAULT_SERVER) + accounts.map { it.server }
    }.distinctUntilChanged()

    val notDefaultRemotes = gitRemotesFlow.combine(defaultServerAliasesFlow) { remotes, aliases ->
      remotes.filter { getUriFromRemoteUrl(it.url)?.host?.let { host -> aliases.contains(host) } == false }
    }
    val discoveredServersFlow = notDefaultRemotes.discoverServers(accountsServersFlow) { remote ->
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

    return gitRemotesFlow.mapToServers(serversFlow, defaultServerAliasesFlow) { servers, aliases, remote ->
      val remoteUri = getUriFromRemoteUrl(remote.url) ?: return@mapToServers null
      if (aliases.contains(remoteUri.host)) {
        GHGitRepositoryMapping.create(GithubServerPath.DEFAULT_SERVER, remote)
      }
      else servers.find { GitHostingUrlUtil.match(it.toURI(), remote.url) }?.let {
        GHGitRepositoryMapping.create(it, remote)
      }
    }.onEach {
      LOG.debug("New list of known repos: $it")
    }
  }

  @ExperimentalCoroutinesApi
  override val knownRepositoriesState: StateFlow<Set<GHGitRepositoryMapping>> =
    knownRepositoriesFlow.stateIn(cs, getStateSharingStartConfig(), emptySet())

  private fun readAliasesFromSettings(): Set<String> =
    AdvancedSettings.getString(GITHUB_ALIASES_SETTING_ID)
      .split(',')
      .asSequence()
      .map { it.trim().lowercase() }
      .filter { it.isNotEmpty() }
      .toSet()

  companion object {
    private val LOG = logger<GHHostedRepositoriesManager>()

    private const val GITHUB_ALIASES_SETTING_ID = "github.aliases"

    private fun getStateSharingStartConfig() =
      if (ApplicationManager.getApplication().isUnitTestMode) SharingStarted.Eagerly else SharingStarted.Lazily
  }
}