// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.update

import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.vcs.update.UpdatedFiles
import git4idea.branch.GitBranchPair
import git4idea.branch.GitBrancher
import git4idea.commands.Git
import git4idea.repo.GitRepository
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

internal class GitResetUpdater(
  val project: Project,
  git: Git,
  val repository: GitRepository,
  private val branchPair: GitBranchPair,
  progressIndicator: ProgressIndicator,
  updatedFiles: UpdatedFiles,
) : GitUpdater(project, git, repository, progressIndicator, updatedFiles) {

  override fun isSaveNeeded(): Boolean = false

  override fun doUpdate(): GitUpdateResult {
    val localBranchName = branchPair.source.name
    val remoteBranchName = branchPair.target.name

    if (!hasRemoteChanges(remoteBranchName)) return GitUpdateResult.NOTHING_TO_UPDATE

    performBranchReset(localBranchName, remoteBranchName)

    return if (!hasRemoteChanges(remoteBranchName)) GitUpdateResult.SUCCESS else GitUpdateResult.ERROR
  }

  private fun performBranchReset(
    localBranchName: @NlsSafe String,
    remoteBranchName: @NlsSafe String,
  ) {
    runBlockingCancellable {
      suspendCancellableCoroutine { continuation ->
        val brancher = GitBrancher.getInstance(project)
        brancher.checkoutNewBranchStartingFrom(localBranchName, remoteBranchName, true, true, listOf(repository)) {
          continuation.resume(Unit)
        }
      }
    }
  }

  override fun toString(): String = "Reset updater"
}
