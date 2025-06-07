// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.util

import git4idea.remote.GitRemoteUrlCoordinates
import git4idea.remote.hosting.HostedGitRepositoryMapping
import git4idea.repo.GitRemote
import git4idea.repo.GitRepository
import git4idea.ui.branch.GitRepositoryMappingData
import org.jetbrains.plugins.gitlab.api.GitLabProjectCoordinates
import org.jetbrains.plugins.gitlab.api.GitLabServerPath

data class GitLabProjectMapping(override val repository: GitLabProjectCoordinates,
                                override val remote: GitRemoteUrlCoordinates)
  : HostedGitRepositoryMapping, GitRepositoryMappingData {
  override val gitRemote: GitRemote
    get() = remote.remote
  override val gitRepository: GitRepository
    get() = remote.repository
  override val repositoryPath: String
    get() = repository.projectPath.name

  companion object {
    fun create(server: GitLabServerPath, remote: GitRemoteUrlCoordinates): GitLabProjectMapping? {
      val repository = GitLabProjectCoordinates.create(server, remote) ?: return null
      return GitLabProjectMapping(repository, remote)
    }
  }
}