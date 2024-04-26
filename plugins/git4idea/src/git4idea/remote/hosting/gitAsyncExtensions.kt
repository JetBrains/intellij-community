// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.remote.hosting

import com.intellij.collaboration.api.ServerPath
import com.intellij.collaboration.util.ComputedResult
import com.intellij.dvcs.repo.VcsRepositoryManager
import com.intellij.dvcs.repo.VcsRepositoryMappingListener
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.progress.coroutineToIndicator
import com.intellij.openapi.project.Project
import git4idea.GitRemoteBranch
import git4idea.branch.GitBranchSyncStatus
import git4idea.commands.Git
import git4idea.commands.GitCommand
import git4idea.commands.GitLineHandler
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

fun GitRepository.infoStateIn(cs: CoroutineScope): StateFlow<GitRepoInfo> = infoFlow().stateIn(cs, SharingStarted.Eagerly, info)

fun GitRepository.infoFlow(): Flow<GitRepoInfo> = channelFlow {
  project.messageBus
    .connect(this)
    .subscribe(GitRepository.GIT_REPO_CHANGE, GitRepositoryChangeListener {
      if (it == this@infoFlow) {
        trySend(it.info)
      }
    })
  send(info)
  awaitClose()
}

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

fun GitRepository.currentRemoteBranchFlow(): Flow<GitRemoteBranch?> =
  infoFlow()
    .map { it.findFirstRemoteBranchTrackedByCurrent() }
    .distinctUntilChanged()

fun GitRepoInfo.findFirstRemoteBranchTrackedByCurrent(): GitRemoteBranch? {
  val currentBranch = currentBranch ?: return null
  return branchTrackInfos.find { it.localBranch == currentBranch }?.remoteBranch
}

private typealias GitRemotesFlow = Flow<Collection<GitRemoteUrlCoordinates>>

/**
 * A flow of sync state between a branch and a local repository state.
 * Branch state is represented by a list of commit hashes in the receiver flow.
 * Receiver flow should emit an ordered list of commits where the last commits in the list is actually the last commits in a branch.
 */
fun Flow<List<String>>.localCommitsSyncStatus(repository: GitRepository): Flow<ComputedResult<GitBranchSyncStatus?>?> {
  val currentRevisionFlow = repository.infoFlow().map { it.currentRevision }.distinctUntilChanged()
  return combine(currentRevisionFlow) { commits, currentRev ->
    if (currentRev == null) ComputedResult.success(null)
    else ComputedResult.compute {
      checkSyncState(repository, currentRev, commits)
    }
  }
}

private suspend fun checkSyncState(repository: GitRepository, currentRev: String, commits: List<String>): GitBranchSyncStatus {
  val headSha = commits.last()
  if (currentRev == headSha) return GitBranchSyncStatus.SYNCED
  if (commits.contains(currentRev)) return GitBranchSyncStatus(true, false)
  if (testCurrentBranchContains(repository, headSha)) return GitBranchSyncStatus(false, true)
  return GitBranchSyncStatus(true, true)
}

private suspend fun testCurrentBranchContains(repository: GitRepository, sha: String): Boolean =
  coroutineToIndicator {
    val h = GitLineHandler(repository.project, repository.root, GitCommand.MERGE_BASE)
    h.setSilent(true)
    h.addParameters("--is-ancestor", sha, "HEAD")
    Git.getInstance().runCommand(h).success()
  }

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