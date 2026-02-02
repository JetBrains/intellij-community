// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab

import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import git4idea.remote.hosting.GitHostingUrlUtil
import git4idea.remote.hosting.HostedGitRepositoriesManager
import git4idea.remote.hosting.discoverServers
import git4idea.remote.hosting.gitRemotesFlow
import git4idea.remote.hosting.mapToServers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.runningFold
import kotlinx.coroutines.flow.stateIn
import org.jetbrains.plugins.gitlab.api.GitLabServerPath
import org.jetbrains.plugins.gitlab.authentication.accounts.GitLabAccountManager
import org.jetbrains.plugins.gitlab.util.GitLabProjectMapping

interface GitLabProjectsManager : HostedGitRepositoriesManager<GitLabProjectMapping>

internal class GitLabProjectsManagerImpl(project: Project, cs: CoroutineScope) : GitLabProjectsManager {

  override val knownRepositoriesState: StateFlow<Set<GitLabProjectMapping>> by lazy {
    val gitRemotesFlow = gitRemotesFlow(project).distinctUntilChanged()

    val accountsServersFlow = service<GitLabAccountManager>().accountsState.map { accounts ->
      mutableSetOf(GitLabServerPath.DEFAULT_SERVER) + accounts.map { it.server }
    }.distinctUntilChanged()

    val discoveredServersFlow = gitRemotesFlow.discoverServers(accountsServersFlow) { remote ->
      GitHostingUrlUtil.findServerAt(LOG, remote) {
        val server = GitLabServerPath(it.toString())
        val isGitLabServer = service<GitLabServersManager>().checkIsGitLabServer(server)
        if (isGitLabServer) server else null
      }
    }.runningFold(emptySet<GitLabServerPath>()) { accumulator, value ->
      accumulator + value
    }.distinctUntilChanged()

    val serversFlow = accountsServersFlow.combine(discoveredServersFlow) { servers1, servers2 ->
      servers1 + servers2
    }

    val knownRepositoriesFlow = gitRemotesFlow.mapToServers(serversFlow) { server, remote ->
      GitLabProjectMapping.create(server, remote)
    }.onEach {
      LOG.debug("New list of known repos: $it")
    }

    knownRepositoriesFlow.stateIn(cs, SharingStarted.Eagerly, emptySet())
  }

  companion object {
    private val LOG = logger<GitLabProjectsManager>()
  }
}