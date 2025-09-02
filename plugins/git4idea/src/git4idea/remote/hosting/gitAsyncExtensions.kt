// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.remote.hosting

import com.intellij.collaboration.api.ServerPath
import com.intellij.collaboration.util.ComputedResult
import com.intellij.dvcs.repo.VcsRepositoryManager
import com.intellij.dvcs.repo.VcsRepositoryMappingListener
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.project.Project
import git4idea.GitRemoteBranch
import git4idea.branch.GitBranchSyncStatus
import git4idea.remote.GitRemoteUrlCoordinates
import git4idea.repo.GitRepoInfo
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryChangeListener
import git4idea.repo.GitRepositoryManager
import kotlinx.coroutines.*
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

fun gitRemotesStateIn(project: Project, cs: CoroutineScope, started: SharingStarted = SharingStarted.Lazily): StateFlow<Set<GitRemoteUrlCoordinates>> =
  gitRemotesFlow(project).stateIn(cs, started, GitRepositoryManager.getInstance(project).collectRemotes())

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
      remote.urls.map { url ->
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
  if (GitCodeReviewUtils.testIsAncestor(repository, headSha, "HEAD")) return GitBranchSyncStatus(false, true)
  return GitBranchSyncStatus(true, true)
}

fun <S : ServerPath, M : HostedGitRepositoryMapping> GitRemotesFlow.mapToServers(
  serversState: Flow<Set<S>>,
  mapper: (S, GitRemoteUrlCoordinates) -> M?,
): Flow<Set<M>> =
  combine(serversState) { remotes, servers ->
    remotes.asSequence().mapNotNull { remote ->
      servers.find { GitHostingUrlUtil.match(it.toURI(), remote.url) }?.let { mapper(it, remote) }
    }.toSet()
  }

fun <S : ServerPath> GitRemotesFlow.discoverServers(
  knownServersFlow: Flow<Set<S>>,
  parallelism: Int = 10,
  checkForDedicatedServer: suspend (GitRemoteUrlCoordinates) -> S?,
): Flow<S> {
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

@OptIn(ExperimentalCoroutinesApi::class)
fun GitRepository.isInCurrentHistory(rev: Flow<String>, ifEqualReturn: Boolean? = null): Flow<Boolean?> {
  val repository = this
  val currentRevFlow = repository.infoFlow().map { it.currentRevision }

  /*
   * Request for the sync state between current local branch and some other branch state.
   * Can't just do combineTransform bc it will not cancel previous computation
   */
  return currentRevFlow.combine(rev) { currentRev, targetRev ->
    currentRev to targetRev
  }.distinctUntilChanged().transformLatest { (currentRev, targetRev) ->
    when (currentRev) {
      null -> emit(null) // does not make sense to update on a no-revision head
      targetRev -> emit(ifEqualReturn)
      else -> supervisorScope {
        emit(null)
        val res = runCatching {
          GitCodeReviewUtils.testIsAncestor(repository, targetRev, currentRev)
        }
        emit(res.getOrNull() ?: false)
      }
    }
  }
}

