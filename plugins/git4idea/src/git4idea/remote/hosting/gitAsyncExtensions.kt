// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.remote.hosting

import com.intellij.collaboration.api.ServerPath
import com.intellij.collaboration.async.combineState
import com.intellij.dvcs.repo.VcsRepositoryManager
import com.intellij.dvcs.repo.VcsRepositoryMappingListener
import com.intellij.openapi.util.Disposer
import git4idea.remote.GitRemoteUrlCoordinates
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryChangeListener
import git4idea.repo.GitRepositoryManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

fun GitRepositoryManager.trackRemotesState(scope: CoroutineScope): StateFlow<Set<GitRemoteUrlCoordinates>> {
  val disposable = Disposer.newDisposable()
  return callbackFlow {
    vcs.project.messageBus.connect(disposable).subscribe(VcsRepositoryManager.VCS_REPOSITORY_MAPPING_UPDATED, VcsRepositoryMappingListener {
      trySend(collectRemotes())
    })
    vcs.project.messageBus.connect(disposable).subscribe(GitRepository.GIT_REPO_CHANGE, GitRepositoryChangeListener {
      trySend(collectRemotes())
    })
    awaitClose {
      Disposer.dispose(disposable)
    }
  }.stateIn(scope, SharingStarted.Lazily, collectRemotes())
}

private fun GitRepositoryManager.collectRemotes(): Set<GitRemoteUrlCoordinates> {
  if (repositories.isEmpty()) {
    return emptySet()
  }

  return repositories.flatMap { repo ->
    repo.remotes.flatMap { remote ->
      remote.urls.mapNotNull { url ->
        GitRemoteUrlCoordinates(url, remote, repo)
      }
    }
  }.toSet()
}

private typealias GitRemotesState = StateFlow<Collection<GitRemoteUrlCoordinates>>

fun <S : ServerPath, M : HostedGitRepositoryMapping> GitRemotesState.mapToServers(scope: CoroutineScope,
                                                                                  serversState: StateFlow<Set<S>>,
                                                                                  mapper: (S, GitRemoteUrlCoordinates) -> M?)
  : StateFlow<Set<M>> {
  return combineState(scope, this, serversState) { remotes, servers ->
    remotes.asSequence().mapNotNull { remote ->
      servers.find { GitHostingUrlUtil.match(it.toURI(), remote.url) }?.let { mapper(it, remote) }
    }.toSet()
  }
}

fun <S : ServerPath> GitRemotesState.discoverServers(scope: CoroutineScope,
                                                     knownServersState: StateFlow<Set<S>>,
                                                     parallelism: Int = 10,
                                                     checkForDedicatedServer: suspend (GitRemoteUrlCoordinates) -> S?)
  : StateFlow<Set<S>> {
  val stateFlow = MutableStateFlow<Set<S>>(emptySet())
  scope.launch {
    combine(knownServersState) { remotes, servers ->
      remotes.filter { remote -> servers.none { GitHostingUrlUtil.match(it.toURI(), remote.url) } }
    }.collect { remotes ->
      remotes.chunked(parallelism).forEach { remotesChunk ->
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