// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.util

import com.intellij.collaboration.async.CancellingScopedDisposable
import com.intellij.collaboration.async.ScopedDisposable
import com.intellij.collaboration.async.combineState
import com.intellij.collaboration.async.mapState
import com.intellij.collaboration.auth.createAccountsFlow
import com.intellij.collaboration.git.GitRemoteUrlCoordinates
import com.intellij.collaboration.git.hosting.GitHostingUrlUtil
import com.intellij.dvcs.repo.VcsRepositoryManager
import com.intellij.dvcs.repo.VcsRepositoryMappingListener
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryChangeListener
import git4idea.repo.GitRepositoryManager
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.future.await
import kotlinx.coroutines.launch
import org.jetbrains.plugins.github.api.GithubServerPath
import org.jetbrains.plugins.github.authentication.accounts.GHAccountManager

@Service
class GHHostedRepositoriesManager(private val project: Project) : ScopedDisposable by CancellingScopedDisposable() {

  private val accountManager: GHAccountManager get() = service()
  private val repositoryManager: GitRepositoryManager get() = project.service()
  private val metadataLoader: GHEnterpriseServerMetadataLoader get() = service()

  val knownRepositoriesState: StateFlow<Set<GHGitRepositoryMapping>>
  val knownRepositories: Set<GHGitRepositoryMapping>
    get() = knownRepositoriesState.value

  init {
    val accountsServersFlow = accountManager.createAccountsFlow(this).mapState(scope) { accounts ->
      accounts.map { it.server }.toSet()
    }

    val remotesFlow = createRemotesFlow()
    val discoveredServersState = if (ApplicationManager.getApplication().isUnitTestMode) {
      MutableStateFlow(emptySet())
    }
    else {
      startServerDiscovery(accountsServersFlow, remotesFlow)
    }

    val knownServersFlow = combineState(scope, accountsServersFlow, discoveredServersState, ::collectServers)

    knownRepositoriesState = combineState(scope, knownServersFlow, remotesFlow, ::collectMappings)
  }

  private fun createRemotesFlow(): StateFlow<Set<GitRemoteUrlCoordinates>> {
    val flow = MutableStateFlow(collectRemotes())
    project.messageBus.connect(this).subscribe(VcsRepositoryManager.VCS_REPOSITORY_MAPPING_UPDATED, VcsRepositoryMappingListener {
      flow.update { collectRemotes() }
    })
    project.messageBus.connect(this).subscribe(GitRepository.GIT_REPO_CHANGE, GitRepositoryChangeListener {
      flow.update { collectRemotes() }
    })
    // update after initial bc can happen outside EDT
    flow.update { collectRemotes() }
    return flow
  }

  private fun collectRemotes(): Set<GitRemoteUrlCoordinates> {
    val gitRepositories = repositoryManager.repositories
    if (gitRepositories.isEmpty()) {
      LOG.debug("No repositories found")
      return emptySet()
    }

    return gitRepositories.flatMap { repo ->
      repo.remotes.flatMap { remote ->
        remote.urls.mapNotNull { url ->
          GitRemoteUrlCoordinates(url, remote, repo)
        }
      }
    }.toSet()
  }

  private fun startServerDiscovery(accountsServersFlow: StateFlow<Set<GithubServerPath>>,
                                   remotesFlow: StateFlow<Collection<GitRemoteUrlCoordinates>>): StateFlow<Set<GithubServerPath>> {
    val stateFlow = MutableStateFlow<Set<GithubServerPath>>(emptySet())

    scope.launch {
      combine(accountsServersFlow, remotesFlow) { servers, remotes ->
        remotes.filter { remote -> servers.none { it.matches(remote.url) } }
      }.collect { remotes ->
        remotes.chunked(URLS_CHECK_PARALLELISM).forEach { remotesChunk ->
          remotesChunk.map { remote ->
            async {
              val server = checkForEnterpriseServer(remote)
              if (server != null) stateFlow.update { it + server }
            }
          }.awaitAll()
        }
      }
    }
    return stateFlow
  }

  private fun collectServers(accountsServers: Set<GithubServerPath>, discoveredServers: Set<GithubServerPath>): Set<GithubServerPath> {
    val servers = mutableSetOf<GithubServerPath>(GithubServerPath.DEFAULT_SERVER)
    servers.addAll(accountsServers)
    servers.addAll(discoveredServers)
    return servers
  }

  private fun collectMappings(servers: Set<GithubServerPath>, remotes: Collection<GitRemoteUrlCoordinates>): Set<GHGitRepositoryMapping> {
    val mappings = HashSet<GHGitRepositoryMapping>()
    for (remote in remotes) {
      val repository = servers.find { it.matches(remote.url) }?.let { GHGitRepositoryMapping.create(it, remote) }
      if (repository != null) {
        mappings.add(repository)
      }
    }
    LOG.debug("New list of known repos: $mappings")
    return mappings
  }

  fun findKnownRepositories(repository: GitRepository): List<GHGitRepositoryMapping> {
    return knownRepositoriesState.value.filter {
      it.gitRemoteUrlCoordinates.repository == repository
    }
  }

  private suspend fun checkForEnterpriseServer(remote: GitRemoteUrlCoordinates): GithubServerPath? {
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
        metadataLoader.loadMetadata(server).await()
        LOG.debug("Found GHE server at $server")
        return server
      }
      catch (ignored: Throwable) {
      }
    }
    return null
  }

  companion object {
    private val LOG = logger<GHHostedRepositoriesManager>()

    private const val URLS_CHECK_PARALLELISM = 10
  }
}