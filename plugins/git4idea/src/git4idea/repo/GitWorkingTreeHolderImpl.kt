// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.repo

import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.vcs.VcsException
import com.intellij.platform.util.coroutines.childScope
import git4idea.GitWorkingTree
import git4idea.commands.Git
import git4idea.remoteApi.GitRepositoryFrontendSynchronizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal class GitWorkingTreeHolderImpl(private val repository: GitRepository) : GitWorkingTreeHolder {
  private val cs = repository.coroutineScope.childScope("GitWorkingTreeHolderImpl")

  private val updateLock = Mutex()
  private val _state = MutableStateFlow<Collection<GitWorkingTree>>(emptyList())

  override fun getWorkingTrees(): Collection<GitWorkingTree> = _state.value

  override fun scheduleReload() {
    cs.launch { updateState() }
  }

  suspend fun updateState() {
    updateLock.withLock {
      _state.value = withContext(Dispatchers.IO) {
        readWorkingTreesFromGit()
      }
      repository.project.messageBus.syncPublisher(GitRepositoryFrontendSynchronizer.TOPIC).workingTreesLoaded(repository)
    }
  }

  private fun readWorkingTreesFromGit(): Collection<GitWorkingTree> {
    LOG.debug { "Reloading working trees for ${repository.root}" }

    val trees = try {
      Git.getInstance().listWorktrees(repository)
    }
    catch (e: VcsException) {
      LOG.info("Failed to list worktrees for ${repository.root}", e)
      return emptyList()
    }

    if (LOG.isDebugEnabled) {
      LOG.debug("Get working trees for ${repository.root}: $trees")
    }

    return trees
  }

  companion object {
    private val LOG = logger<GitWorkingTreeHolderImpl>()
  }
}