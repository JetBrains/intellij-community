// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.repo

import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.util.BackgroundTaskUtil
import git4idea.GitWorkingTree
import git4idea.commands.Git
import git4idea.remoteApi.GitRepositoryFrontendSynchronizer
import git4idea.workingTrees.GitListWorktreeLineListener

class GitWorkingTreeHolderImpl(repository: GitRepository) : GitWorkingTreeHolder,
                                                            GitRepositoryDataHolder(repository, "GitWorkingTreeHolder") {
  private var workingTrees: Collection<GitWorkingTree> = emptyList()

  override fun getWorkingTrees(): Collection<GitWorkingTree> {
    return workingTrees
  }

  override suspend fun updateState() {
    workingTrees = readWorkingTreesFromGit()
    BackgroundTaskUtil.syncPublisher(repository.project, GitRepositoryFrontendSynchronizer.TOPIC).workingTreesLoaded(repository)
  }

  private fun readWorkingTreesFromGit(): Collection<GitWorkingTree> {
    LOG.debug { "Reloading working trees for ${repository.root}" }

    val listener = GitListWorktreeLineListener(repository)
    val commandResult = Git.getInstance().listWorktrees(repository, listener)
    if (!commandResult.success()) {
      LOG.info("Failed to list worktrees: $commandResult.errorOutputAsJoinedString")
    }

    if (LOG.isDebugEnabled) {
      LOG.debug("Get working trees for ${repository.root}: ${listener.trees}")
    }

    return listener.trees
  }

  companion object {
    private val LOG = logger<GitWorkingTreeHolderImpl>()
  }
}