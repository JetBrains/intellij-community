// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.util

import git4idea.repo.GitRemote
import git4idea.repo.GitRepository
import git4idea.ui.branch.GitRepositoryMappingData
import org.jetbrains.plugins.github.api.GHRepositoryCoordinates
import org.jetbrains.plugins.github.api.GithubServerPath

class GHGitRepositoryMapping(val ghRepositoryCoordinates: GHRepositoryCoordinates, val gitRemoteUrlCoordinates: GitRemoteUrlCoordinates) : GitRepositoryMappingData {
  override val gitRemote: GitRemote
    get() = gitRemoteUrlCoordinates.remote
  override val gitRepository: GitRepository
    get() = gitRemoteUrlCoordinates.repository
  override val repositoryPath: String
    get() = ghRepositoryCoordinates.repositoryPath.repository

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is GHGitRepositoryMapping) return false

    if (ghRepositoryCoordinates != other.ghRepositoryCoordinates) return false

    return true
  }

  override fun hashCode(): Int {
    return ghRepositoryCoordinates.hashCode()
  }

  override fun toString(): String {
    return "(repository=$ghRepositoryCoordinates, remote=$gitRemoteUrlCoordinates)"
  }

  companion object {
    fun create(server: GithubServerPath, remote: GitRemoteUrlCoordinates): GHGitRepositoryMapping? {
      val repositoryPath = GithubUrlUtil.getUserAndRepositoryFromRemoteUrl(remote.url) ?: return null
      val repository = GHRepositoryCoordinates(server, repositoryPath)
      return GHGitRepositoryMapping(repository, remote)
    }
  }
}