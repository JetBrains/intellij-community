// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.remoteApi

import com.intellij.platform.project.ProjectId
import com.intellij.platform.vcs.impl.shared.RepositoryId
import com.intellij.vcs.git.rpc.GitOperationsApi
import com.intellij.vcs.rpc.ProjectScopeRpcHelper.projectScoped
import git4idea.GitStandardLocalBranch
import git4idea.branch.GitBrancher
import git4idea.repo.GitRepositoryIdCache
import git4idea.ui.branch.updateBranches
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal class GitOperationsApiImpl : GitOperationsApi {
  override suspend fun checkoutAndUpdate(projectId: ProjectId, repositories: List<RepositoryId>, branch: GitStandardLocalBranch): Deferred<Unit> =
    coroutineScope {
      async {
        projectScoped(projectId) { project ->
          requireOwner()
          val branchName = branch.name
          val resolvedRepositories = GitRepositoryIdCache.getInstance(project).resolveAll(repositories)
          val brancher = GitBrancher.getInstance(project)

          suspendCancellableCoroutine { continuation ->
            brancher.checkout(branchName, false, resolvedRepositories) {
              val repositoriesToUpdate = resolvedRepositories.filter { repo -> repo.currentBranchName == branchName }
              if (repositoriesToUpdate.isEmpty()) {
                continuation.resume(Unit)
                return@checkout
              }
              val updateJob = updateBranches(project, repositoriesToUpdate, listOf(branchName))
              updateJob.invokeOnCompletion { throwable ->
                if (throwable != null) {
                  continuation.resumeWithException(throwable)
                }
                else {
                  continuation.resume(Unit)
                }
              }
            }
          }
        }
      }
    }
}

