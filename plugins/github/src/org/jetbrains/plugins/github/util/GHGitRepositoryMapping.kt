// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.util

import git4idea.remote.GitRemoteUrlCoordinates
import git4idea.remote.hosting.HostedGitRepositoryMapping
import git4idea.repo.GitRemote
import git4idea.repo.GitRepository
import git4idea.ui.branch.GitRepositoryMappingData
import org.jetbrains.plugins.github.api.GHRepositoryCoordinates
import org.jetbrains.plugins.github.api.GithubServerPath

class GHGitRepositoryMapping(override val repository: GHRepositoryCoordinates,
                             override val remote: GitRemoteUrlCoordinates)
  : GitRepositoryMappingData, HostedGitRepositoryMapping {

  override val gitRemote: GitRemote
    get() = remote.remote
  override val gitRepository: GitRepository
    get() = remote.repository
  override val repositoryPath: String
    get() = repository.repositoryPath.repository

  @Deprecated("use repository property", ReplaceWith("repository"))
  val ghRepositoryCoordinates: GHRepositoryCoordinates = repository

  @Deprecated("use remote property", ReplaceWith("remote"))
  val gitRemoteUrlCoordinates: org.jetbrains.plugins.github.util.GitRemoteUrlCoordinates = GitRemoteUrlCoordinates(remote)

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is GHGitRepositoryMapping) return false

    if (repository != other.repository) return false

    return true
  }

  override fun hashCode(): Int {
    return repository.hashCode()
  }

  override fun toString(): String {
    return "(repository=$repository, remote=$repository)"
  }

  companion object {
    fun create(server: GithubServerPath, remote: GitRemoteUrlCoordinates): GHGitRepositoryMapping? {
      val repositoryPath = GithubUrlUtil.getUserAndRepositoryFromRemoteUrl(remote.url) ?: return null
      val repository = GHRepositoryCoordinates(server, repositoryPath)
      return GHGitRepositoryMapping(repository, remote)
    }
  }
}