// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.remote.hosting

import com.intellij.dvcs.repo.VcsRepositoryManager
import com.intellij.dvcs.repo.VcsRepositoryMappingListener
import com.intellij.openapi.Disposable
import git4idea.remote.GitRemoteUrlCoordinates
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryChangeListener
import git4idea.repo.GitRepositoryManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

internal fun GitRepositoryManager.createRemotesFlow(disposable: Disposable): StateFlow<Set<GitRemoteUrlCoordinates>> {
  val flow = MutableStateFlow(collectRemotes())
  vcs.project.messageBus.connect(disposable).subscribe(VcsRepositoryManager.VCS_REPOSITORY_MAPPING_UPDATED, VcsRepositoryMappingListener {
    flow.update { collectRemotes() }
  })
  vcs.project.messageBus.connect(disposable).subscribe(GitRepository.GIT_REPO_CHANGE, GitRepositoryChangeListener {
    flow.update { collectRemotes() }
  })
  // update after initial bc can happen outside EDT
  flow.update { collectRemotes() }
  return flow
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