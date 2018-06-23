// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.util

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import git4idea.GitUtil
import git4idea.repo.GitRepository
import org.jetbrains.plugins.github.api.GithubApiUtil
import org.jetbrains.plugins.github.api.GithubFullPath
import org.jetbrains.plugins.github.api.GithubRepositoryPath
import org.jetbrains.plugins.github.api.GithubServerPath
import org.jetbrains.plugins.github.authentication.GithubAuthenticationManager

/**
 * Utilities for Github-Git interactions
 */
class GithubGitHelper(private val githubSettings: GithubSettings,
                      private val authenticationManager: GithubAuthenticationManager,
                      private val migrationHelper: GithubAccountsMigrationHelper) {
  private val DEFAULT_SERVER = GithubServerPath(GithubApiUtil.DEFAULT_GITHUB_HOST)

  fun getRemoteUrl(server: GithubServerPath, repoPath: GithubFullPath): String {
    return getRemoteUrl(server, repoPath.user, repoPath.repository)
  }

  fun getRemoteUrl(server: GithubServerPath, user: String, repo: String): String {
    return if (githubSettings.isCloneGitUsingSsh) {
      "git@${server.host}:${server.suffix?.substring(1).orEmpty()}/$user/$repo.git"
    }
    else {
      "https://${server.host}${server.suffix.orEmpty()}/$user/$repo.git"
    }
  }

  fun getAccessibleRemoteUrls(repository: GitRepository): List<String> {
    return repository.remotes.map { it.urls }.flatten().filter(::isRemoteUrlAccessible)
  }

  fun hasAccessibleRemotes(repository: GitRepository): Boolean {
    return repository.remotes.map { it.urls }.flatten().any(::isRemoteUrlAccessible)
  }

  private fun isRemoteUrlAccessible(url: String) = authenticationManager.getAccounts().find { it.server.matches(url) } != null

  fun getPossibleRepositories(repository: GitRepository): Set<GithubRepositoryPath> {
    val registeredServers = mutableSetOf(DEFAULT_SERVER)
    migrationHelper.getOldServer()?.run(registeredServers::add)
    authenticationManager.getAccounts().mapTo(registeredServers) { it.server }
    val repositoryPaths = mutableSetOf<GithubRepositoryPath>()
    for (url in repository.remotes.map { it.urls }.flatten()) {
      registeredServers.filter { it.matches(url) }
        .mapNotNullTo(repositoryPaths, { server ->
          GithubUrlUtil.getUserAndRepositoryFromRemoteUrl(url)?.let { GithubRepositoryPath(server, it) }
        })
    }
    return repositoryPaths
  }

  companion object {
    @JvmStatic
    fun findGitRepository(project: Project, file: VirtualFile?): GitRepository? {
      val manager = GitUtil.getRepositoryManager(project)
      val repositories = manager.repositories
      if (repositories.size == 0) {
        return null
      }
      if (repositories.size == 1) {
        return repositories[0]
      }
      if (file != null) {
        val repository = manager.getRepositoryForFileQuick(file)
        if (repository != null) {
          return repository
        }
      }
      return manager.getRepositoryForFileQuick(project.baseDir)
    }

    @JvmStatic
    fun getInstance(): GithubGitHelper {
      return service()
    }
  }
}