// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.api

import com.intellij.collaboration.util.resolveRelative
import com.intellij.openapi.util.NlsSafe
import git4idea.remote.GitRemoteUrlCoordinates
import git4idea.remote.hosting.HostedRepositoryCoordinates
import kotlinx.serialization.Serializable
import org.jetbrains.plugins.gitlab.util.GitLabProjectPath
import java.net.URI

@Serializable
data class GitLabProjectCoordinates(override val serverPath: GitLabServerPath, val projectPath: GitLabProjectPath)
  : HostedRepositoryCoordinates {

  override fun getWebURI(): URI = serverPath.toURI().resolveRelative(projectPath.fullPath())

  @NlsSafe
  override fun toString(): String = "$serverPath/$projectPath"

  companion object {
    fun create(server: GitLabServerPath, remote: GitRemoteUrlCoordinates): GitLabProjectCoordinates? {
      val projectPath = GitLabProjectPath.create(server, remote) ?: return null
      return GitLabProjectCoordinates(server, projectPath)
    }
  }
}