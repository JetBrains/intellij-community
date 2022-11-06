// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.project

import com.intellij.util.text.nullize
import git4idea.remote.GitRemoteUrlCoordinates
import git4idea.remote.hosting.GitHostingUrlUtil
import org.jetbrains.plugins.gitlab.api.GitLabServerPath

data class GitLabProjectPath(val owner: String, val name: String) {
  fun fullPath(): String = "$owner/$name"

  companion object {
    fun create(server: GitLabServerPath, remote: GitRemoteUrlCoordinates): GitLabProjectPath? {
      val serverPath = server.toURI().path
      val remotePath = GitHostingUrlUtil.getUriFromRemoteUrl(remote.url)?.path ?: return null

      if (!remotePath.startsWith(serverPath)) return null
      val repositoryPath = remotePath.removePrefix(serverPath).removePrefix("/")

      val lastSlashIdx = repositoryPath.lastIndexOf('/')
      if (lastSlashIdx < 0) return null

      val name = repositoryPath.substringAfterLast('/', "").nullize() ?: return null
      val owner = repositoryPath.substringBeforeLast('/', "").nullize() ?: return null
      return GitLabProjectPath(owner, name)
    }
  }
}