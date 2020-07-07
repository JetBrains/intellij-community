// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.util

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import git4idea.GitUtil
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryManager
import org.jetbrains.plugins.github.api.GHRepositoryPath
import org.jetbrains.plugins.github.api.GithubServerPath
import org.jetbrains.plugins.github.authentication.GithubAuthenticationManager

/**
 * Utilities for Github-Git interactions
 *
 * accessible url - url that matches at least one registered account
 * possible url - accessible urls + urls that match github.com + urls that match server saved in old settings
 */
@Service
class GithubGitHelper {
  fun getRemoteUrl(server: GithubServerPath, repoPath: GHRepositoryPath): String {
    return getRemoteUrl(server, repoPath.owner, repoPath.repository)
  }

  fun getRemoteUrl(server: GithubServerPath, user: String, repo: String): String {
    return if (GithubSettings.getInstance().isCloneGitUsingSsh) {
      "git@${server.host}:${server.suffix?.substring(1).orEmpty()}/$user/$repo.git"
    }
    else {
      "https://${server.host}${server.suffix.orEmpty()}/$user/$repo.git"
    }
  }

  fun getAccessibleRemoteUrls(repository: GitRepository): List<String> {
    return repository.getRemoteUrls().filter(::isRemoteUrlAccessible)
  }

  fun hasAccessibleRemotes(repository: GitRepository): Boolean {
    return repository.getRemoteUrls().any(::isRemoteUrlAccessible)
  }

  private fun isRemoteUrlAccessible(url: String) = GithubAuthenticationManager.getInstance().getAccounts().find { it.server.matches(url) } != null

  fun getPossibleRepositories(repository: GitRepository): Set<GHGitRepositoryMapping> {
    val knownServers = getKnownGithubServers()
    return repository.remotes.flatMap { remote ->
      remote.urls.mapNotNull { url ->
        knownServers.find { it.matches(url) }?.let { GHGitRepositoryMapping.create(it, GitRemoteUrlCoordinates(url, remote, repository)) }
      }
    }.toSet()
  }

  fun getPossibleRepositories(project: Project): Set<GHGitRepositoryMapping> {
    val repositories = project.service<GitRepositoryManager>().repositories
    if (repositories.isEmpty()) return emptySet()

    val knownServers = getKnownGithubServers()

    return repositories.flatMap { repo ->
      repo.remotes.flatMap { remote ->
        remote.urls.mapNotNull { url ->
          knownServers.find { it.matches(url) }?.let { GHGitRepositoryMapping.create(it, GitRemoteUrlCoordinates(url, remote, repo)) }
        }
      }
    }.toSet()
  }

  fun havePossibleRemotes(project: Project): Boolean {
    val repositories = project.service<GitRepositoryManager>().repositories
    if (repositories.isEmpty()) return false

    val knownServers = getKnownGithubServers()
    return repositories.any { repo -> repo.getRemoteUrls().any { url -> knownServers.any { it.matches(url) } } }
  }

  private fun getKnownGithubServers(): Set<GithubServerPath> {
    val registeredServers = mutableSetOf(GithubServerPath.DEFAULT_SERVER)
    GithubAccountsMigrationHelper.getInstance().getOldServer()?.run(registeredServers::add)
    GithubAuthenticationManager.getInstance().getAccounts().mapTo(registeredServers) { it.server }
    return registeredServers
  }

  private fun GitRepository.getRemoteUrls() = remotes.map { it.urls }.flatten()

  companion object {
    @JvmStatic
    fun findGitRepository(project: Project, file: VirtualFile? = null): GitRepository? {
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