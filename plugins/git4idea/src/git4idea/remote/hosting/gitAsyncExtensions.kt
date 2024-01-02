// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.remote.hosting

import com.intellij.collaboration.api.ServerPath
import com.intellij.dvcs.repo.VcsRepositoryManager
import com.intellij.dvcs.repo.VcsRepositoryMappingListener
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.project.Project
import git4idea.remote.GitRemoteUrlCoordinates
import git4idea.repo.GitRepoInfo
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryChangeListener
import git4idea.repo.GitRepositoryManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*

fun GitRepository.changesSignalFlow(): Flow<Unit> = channelFlow {
  project.messageBus
    .connect(this)
    .subscribe(GitRepository.GIT_REPO_CHANGE, GitRepositoryChangeListener {
      if (it == this@changesSignalFlow) {
        trySend(Unit)
      }
    })
  awaitClose()
}

fun GitRepository.infoStateIn(cs: CoroutineScope): StateFlow<GitRepoInfo> = channelFlow {
  project.messageBus
    .connect(this)
    .subscribe(GitRepository.GIT_REPO_CHANGE, GitRepositoryChangeListener {
      if (it == this@infoStateIn) {
        trySend(it.info)
      }
    })
  awaitClose()
}.stateIn(cs, SharingStarted.Eagerly, info)

fun gitRemotesFlow(project: Project): Flow<Set<GitRemoteUrlCoordinates>> =
  callbackFlow {
    val repoManager = project.serviceAsync<GitRepositoryManager>()
    val cs = this
    project.messageBus.connect(cs).subscribe(VcsRepositoryManager.VCS_REPOSITORY_MAPPING_UPDATED, VcsRepositoryMappingListener {
      trySend(repoManager.collectRemotes())
    })
    project.messageBus.connect(cs).subscribe(GitRepository.GIT_REPO_CHANGE, GitRepositoryChangeListener {
      trySend(repoManager.collectRemotes())
    })
    send(repoManager.collectRemotes())
    awaitClose()
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

private typealias GitRemotesFlow = Flow<Collection<GitRemoteUrlCoordinates>>

fun <S : ServerPath, M : HostedGitRepositoryMapping> GitRemotesFlow.mapToServers(serversState: Flow<Set<S>>,
                                                                                 mapper: (S, GitRemoteUrlCoordinates) -> M?)
  : Flow<Set<M>> =
  combine(serversState) { remotes, servers ->
    remotes.asSequence().mapNotNull { remote ->
      servers.find { GitHostingUrlUtil.match(it.toURI(), remote.url) }?.let { mapper(it, remote) }
    }.toSet()
  }

fun <S : ServerPath> GitRemotesFlow.discoverServers(knownServersFlow: Flow<Set<S>>,
                                                    parallelism: Int = 10,
                                                    checkForDedicatedServer: suspend (GitRemoteUrlCoordinates) -> S?)
  : Flow<S> {
  val remotesFlow = this
  return channelFlow {
    remotesFlow.combine(knownServersFlow) { remotes, servers ->
      remotes.filter { remote -> servers.none { GitHostingUrlUtil.match(it.toURI(), remote.url) } }
    }
      .conflate()
      .collect { remotes ->
        remotes.chunked(parallelism).forEach { remotesChunk ->
          remotesChunk.map { remote ->
            async {
              val server = try {
                checkForDedicatedServer(remote)
              }
              catch (e: Exception) {
                null
              }
              if (server != null) send(server)
            }
          }.awaitAll()
        }
      }
  }
}