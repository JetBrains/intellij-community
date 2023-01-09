// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab

import com.intellij.collaboration.async.disposingScope
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import git4idea.remote.hosting.HostedGitRepositoriesManager
import git4idea.remote.hosting.gitRemotesFlow
import git4idea.remote.hosting.mapToServers
import kotlinx.coroutines.flow.*
import org.jetbrains.plugins.gitlab.api.GitLabServerPath
import org.jetbrains.plugins.gitlab.authentication.accounts.GitLabAccountManager
import org.jetbrains.plugins.gitlab.util.GitLabProjectMapping

interface GitLabProjectsManager : HostedGitRepositoriesManager<GitLabProjectMapping>

internal class GitLabProjectsManagerImpl(project: Project) : GitLabProjectsManager, Disposable {

  override val knownRepositoriesState: StateFlow<Set<GitLabProjectMapping>> by lazy {
    val gitRemotesFlow = gitRemotesFlow(project).distinctUntilChanged()

    val accountsServersFlow = service<GitLabAccountManager>().accountsState.map { accounts ->
      mutableSetOf(GitLabServerPath.DEFAULT_SERVER) + accounts.map { it.server }
    }.distinctUntilChanged()

    val knownRepositoriesFlow = gitRemotesFlow.mapToServers(accountsServersFlow) { server, remote ->
      GitLabProjectMapping.create(server, remote)
    }.onEach {
      LOG.debug("New list of known repos: $it")
    }

    knownRepositoriesFlow.stateIn(disposingScope(), SharingStarted.Eagerly, emptySet())
  }

  override fun dispose() = Unit

  companion object {
    private val LOG = logger<GitLabProjectsManager>()
  }
}