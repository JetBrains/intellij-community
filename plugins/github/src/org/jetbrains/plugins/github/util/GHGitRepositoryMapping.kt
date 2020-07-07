// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.util

import org.jetbrains.plugins.github.api.GHRepositoryCoordinates
import org.jetbrains.plugins.github.api.GithubServerPath

class GHGitRepositoryMapping(val repository: GHRepositoryCoordinates, val remote: GitRemoteUrlCoordinates) {

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
    return "(repository=$repository, remote=$remote)"
  }

  companion object {
    fun create(server: GithubServerPath, remote: GitRemoteUrlCoordinates): GHGitRepositoryMapping? {
      val repositoryPath = GithubUrlUtil.getUserAndRepositoryFromRemoteUrl(remote.url) ?: return null
      val repository = GHRepositoryCoordinates(server, repositoryPath)
      return GHGitRepositoryMapping(repository, remote)
    }
  }
}