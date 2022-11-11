// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.remote.hosting

import com.intellij.collaboration.api.ServerPath
import com.intellij.collaboration.async.*
import com.intellij.collaboration.auth.AccountManager
import com.intellij.collaboration.auth.ServerAccount
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import git4idea.remote.GitRemoteUrlCoordinates
import git4idea.repo.GitRepositoryManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

abstract class MappingHostedGitRepositoriesManager<S : ServerPath, M : HostedGitRepositoryMapping>(
  private val logger: Logger,
  project: Project,
  serverDataSupplier: ServersDataSupplier<S>
) : HostedGitRepositoriesManager<M>, Disposable {

  protected val scope = disposingScope()

  final override val knownRepositoriesState: StateFlow<Set<M>>

  init {
    val serversState = serverDataSupplier.also {
      Disposer.register(this, it)
    }.serversState

    @Suppress("LeakingThis")
    val remotesState: StateFlow<Set<GitRemoteUrlCoordinates>> = project.service<GitRepositoryManager>().createRemotesFlow(this)

    knownRepositoriesState = combineState(scope, serversState, remotesState) { servers, remotes ->
      remotes.asSequence().mapNotNull { remote ->
        servers.find { GitHostingUrlUtil.match(it.toURI(), remote.url) }?.let { createMapping(it, remote) }
      }.toSet()
    }

    scope.launch {
      knownRepositoriesState.collectLatest {
        logger.debug("New list of known repos: $it")
      }
    }
  }

  protected abstract fun createMapping(server: S, remote: GitRemoteUrlCoordinates): M?

  override fun dispose() = Unit
}

interface ServersDataSupplier<S : ServerPath> : Disposable {
  val serversState: StateFlow<Set<S>>
}

abstract class DiscoveringAuthenticatingServersStateSupplier<A : ServerAccount, S : ServerPath>(
  project: Project,
  private val defaultServer: S? = null
) : ServersDataSupplier<S>, Disposable {

  protected val scope = disposingScope()

  private val accountManager: AccountManager<out A, *> get() = accountManager()
  protected abstract fun accountManager(): AccountManager<out A, *>

  final override val serversState: StateFlow<Set<S>>

  init {
    val accountsServersFlow: StateFlow<Set<S>> = accountManager.accountsState.mapState(scope) { accounts ->
      accounts.keys.map(::getServer).toSet()
    }

    @Suppress("LeakingThis")
    val remotesFlow = project.service<GitRepositoryManager>().createRemotesFlow(this)
    val discoveredServersState = if (ApplicationManager.getApplication().isUnitTestMode) {
      MutableStateFlow(emptySet())
    }
    else {
      startServerDiscovery(accountsServersFlow, remotesFlow)
    }

    serversState = combineState(scope, accountsServersFlow, discoveredServersState, ::collectServers)
  }

  private fun collectServers(accountsServers: Set<S>, discoveredServers: Set<S>): Set<S> {
    val servers = mutableSetOf<S>()
    if (defaultServer != null) servers.add(defaultServer)
    servers.addAll(accountsServers)
    servers.addAll(discoveredServers)
    return servers
  }

  private fun startServerDiscovery(accountsServersFlow: StateFlow<Set<S>>,
                                   remotesFlow: StateFlow<Collection<GitRemoteUrlCoordinates>>): StateFlow<Set<S>> {
    val stateFlow = MutableStateFlow<Set<S>>(emptySet())

    scope.launch {
      combine(accountsServersFlow, remotesFlow) { servers, remotes ->
        remotes.filter { remote -> servers.none { GitHostingUrlUtil.match(it.toURI(), remote.url) } }
      }.collect { remotes ->
        remotes.chunked(URLS_CHECK_PARALLELISM).forEach { remotesChunk ->
          remotesChunk.map { remote ->
            async {
              val server = checkForDedicatedServer(remote)
              if (server != null) stateFlow.update { it + server }
            }
          }.awaitAll()
        }
      }
    }
    return stateFlow
  }

  protected abstract fun getServer(account: A): S

  protected abstract suspend fun checkForDedicatedServer(remote: GitRemoteUrlCoordinates): S?

  override fun dispose() = Unit

  companion object {
    private const val URLS_CHECK_PARALLELISM = 10
  }
}

