// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.util

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import git4idea.GitLocalBranch
import git4idea.GitUtil
import git4idea.branch.GitBranchUtil
import git4idea.push.GitPushTarget
import git4idea.repo.GitRemote
import git4idea.repo.GitRepository
import org.jetbrains.plugins.github.api.GHRepositoryPath
import org.jetbrains.plugins.github.api.GithubServerPath

/**
 * Utilities for Github-Git interactions
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

  fun findRemote(repository: GitRepository, httpUrl: String?, sshUrl: String?): GitRemote? =
    repository.remotes.find {
      it.firstUrl != null && (it.firstUrl == httpUrl ||
                              it.firstUrl == httpUrl + GitUtil.DOT_GIT ||
                              it.firstUrl == sshUrl ||
                              it.firstUrl == sshUrl + GitUtil.DOT_GIT)
    }

  fun findLocalBranch(repository: GitRepository, prRemote: GitRemote, isFork: Boolean, possibleBranchName: String?): String? {
    val localBranchesWithTracking =
      with(repository.branches) {
        if (isFork) {
          localBranches.filter { it.findTrackedBranch(repository)?.remote == prRemote }
        }
        else {
          val prRemoteBranch = remoteBranches.find { it.nameForRemoteOperations == possibleBranchName } ?: return null
          localBranches.filter { it.findTrackedBranch(repository) == prRemoteBranch }
        }
      }
    return localBranchesWithTracking.find { it.name == possibleBranchName }?.name
           // if PR was made not from fork we can assume that the first local branch with tracking to that fork is a good candidate of local branch for that PR.
           ?: if (!isFork) localBranchesWithTracking.firstOrNull()?.name else null
  }

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

    fun findPushTarget(repository: GitRepository, remote: GitRemote, branch: GitLocalBranch) =
      GitPushTarget.getFromPushSpec(repository, remote, branch)
      ?: GitBranchUtil.getTrackInfoForBranch(repository, branch)
        ?.takeIf { it.remote == remote }
        ?.let { GitPushTarget(it.remoteBranch, false) }

    @JvmStatic
    fun getInstance(): GithubGitHelper {
      return service()
    }
  }
}
