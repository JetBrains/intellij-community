// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.util

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.Pair
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VirtualFile
import git4idea.remote.hosting.GitHostingUrlUtil.match
import git4idea.repo.GitRemote
import git4idea.repo.GitRepository
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.github.authentication.GithubAuthenticationManager
import java.net.UnknownHostException

/**
 * Various utility methods for the GutHub plugin.
 */
object GithubUtil {

  @JvmField
  val LOG: Logger = Logger.getInstance("github")

  @NlsSafe
  const val SERVICE_DISPLAY_NAME: String = "GitHub"

  @NlsSafe
  const val ENTERPRISE_SERVICE_DISPLAY_NAME: String = "GitHub Enterprise"
  const val GIT_AUTH_PASSWORD_SUBSTITUTE: String = "x-oauth-basic"

  @NlsSafe
  @JvmStatic
  fun getErrorTextFromException(e: Throwable): String {
    return if (e is UnknownHostException) {
      "Unknown host: " + e.message
    }
    else StringUtil.notNullize(e.message, "Unknown error")
  }

  @JvmStatic
  @Deprecated("{@link GithubGitHelper}", ReplaceWith("GithubGitHelper.findGitRepository(project, file)",
                                                     "org.jetbrains.plugins.github.util.GithubGitHelper"))
  @ApiStatus.ScheduledForRemoval
  fun getGitRepository(project: Project, file: VirtualFile?): GitRepository? {
    return GithubGitHelper.findGitRepository(project, file)
  }

  @JvmStatic
  @Deprecated("{@link GithubGitHelper}")
  private fun findGithubRemoteUrl(repository: GitRepository): String? {
    val remote = findGithubRemote(repository) ?: return null
    return remote.getSecond()
  }

  @JvmStatic
  @Deprecated("{@link org.jetbrains.plugins.github.api.GithubServerPath}, {@link GithubGitHelper}")
  private fun findGithubRemote(repository: GitRepository): Pair<GitRemote, String>? {
    val server = GithubAuthenticationManager.getInstance().getSingleOrDefaultAccount(repository.project)?.server ?: return null

    var githubRemote: Pair<GitRemote, String>? = null
    for (gitRemote in repository.remotes) {
      for (remoteUrl in gitRemote.urls) {
        if (match(server.toURI(), remoteUrl)) {
          val remoteName = gitRemote.name
          if ("github" == remoteName || "origin" == remoteName) {
            return Pair.create(gitRemote, remoteUrl)
          }
          if (githubRemote == null) {
            githubRemote = Pair.create(gitRemote, remoteUrl)
          }
          break
        }
      }
    }
    return githubRemote
  }

  @JvmStatic
  @Deprecated("{@link org.jetbrains.plugins.github.api.GithubServerPath}")
  @ApiStatus.ScheduledForRemoval
  fun isRepositoryOnGitHub(repository: GitRepository): Boolean {
    return findGithubRemoteUrl(repository) != null
  }
}
