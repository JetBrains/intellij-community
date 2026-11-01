// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.util

import git4idea.remote.GitRemoteUrlCoordinates
import git4idea.remote.hosting.GitHostingUrlUtil
import org.jetbrains.plugins.gitlab.api.GitLabProjectCoordinates
import org.jetbrains.plugins.gitlab.api.GitLabServerPath

/**
 * Gets the project path from [gitRemoteUrl].
 * SSH URLs use the full project path. HTTP URLs include the web path of [server], which this function removes.
 */
fun GitLabProjectPath.Companion.create(server: GitLabServerPath, gitRemoteUrl: String): GitLabProjectPath? {
  val serverPath = server.toURI().path
  val remotePath = GitHostingUrlUtil.getUriFromRemoteUrl(gitRemoteUrl)?.path ?: return null

  val repositoryPath = when {
    GitHostingUrlUtil.isSshUrl(gitRemoteUrl) -> remotePath
    remotePath.startsWith(serverPath) -> remotePath.removePrefix(serverPath)
    else -> return null
  }.removePrefix("/")

  return extractProjectPath(repositoryPath)
}

fun GitLabProjectCoordinates.Companion.create(server: GitLabServerPath, remote: GitRemoteUrlCoordinates): GitLabProjectCoordinates? {
  val projectPath = GitLabProjectPath.create(server, remote.url) ?: return null
  return GitLabProjectCoordinates(server, projectPath)
}
