// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab

import com.intellij.collaboration.async.disposingScope
import com.intellij.collaboration.async.mapState
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import git4idea.remote.hosting.HostedGitRepositoriesManager
import git4idea.remote.hosting.trackRemotesState
import git4idea.remote.hosting.mapToServers
import git4idea.repo.GitRepositoryManager
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.jetbrains.plugins.gitlab.api.GitLabServerPath
import org.jetbrains.plugins.gitlab.authentication.accounts.GitLabAccountManager
import org.jetbrains.plugins.gitlab.util.GitLabProjectMapping

interface GitLabProjectsManager : HostedGitRepositoriesManager<GitLabProjectMapping>

internal class GitLabProjectsManagerImpl(project: Project) : GitLabProjectsManager, Disposable {

  override val knownRepositoriesState: StateFlow<Set<GitLabProjectMapping>>

  init {
    val scope = disposingScope()

    val gitRemotesState = project.service<GitRepositoryManager>().trackRemotesState(scope)

    val serversState = service<GitLabAccountManager>().accountsState.mapState(scope) { accounts ->
      mutableSetOf(GitLabServerPath.DEFAULT_SERVER) + accounts.map { it.server }
    }

    knownRepositoriesState = gitRemotesState.mapToServers(scope, serversState) { server, remote ->
      GitLabProjectMapping.create(server, remote)
    }

    scope.launch {
      knownRepositoriesState.collect {
        LOG.debug("New list of known repos: $it")
      }
    }
  }

  override fun dispose() = Unit

  companion object {
    private val LOG = logger<GitLabProjectsManager>()
  }
}