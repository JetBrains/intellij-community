// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.api

import com.intellij.openapi.util.NlsSafe
import git4idea.remote.GitRemoteUrlCoordinates
import git4idea.remote.hosting.HostedRepositoryCoordinates
import org.jetbrains.plugins.gitlab.project.GitLabProjectPath

data class GitLabProjectCoordinates(override val serverPath: GitLabServerPath, val projectPath: GitLabProjectPath)
  : HostedRepositoryCoordinates {
  @NlsSafe
  override fun toString(): String = "$serverPath/$projectPath"

  companion object {
    fun create(server: GitLabServerPath, remote: GitRemoteUrlCoordinates): GitLabProjectCoordinates? {
      val projectPath = GitLabProjectPath.create(server, remote) ?: return null
      return GitLabProjectCoordinates(server, projectPath)
    }
  }
}