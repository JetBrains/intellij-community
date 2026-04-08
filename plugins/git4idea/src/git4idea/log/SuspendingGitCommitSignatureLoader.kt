// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.log

import com.intellij.collaboration.async.childScope
import com.intellij.openapi.application.UI
import com.intellij.openapi.diagnostic.getOrHandleException
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.checkCanceled
import com.intellij.openapi.progress.coroutineToIndicator
import com.intellij.openapi.project.Project
import com.intellij.platform.util.coroutines.sync.OverflowSemaphore
import com.intellij.util.system.LowLevelLocalMachineAccess
import com.intellij.util.system.OS
import com.intellij.vcs.log.CommitId
import com.intellij.vcs.log.data.util.VcsCommitsDataLoader
import git4idea.commit.signature.GitCommitSignature
import git4idea.repo.GitRepositoryManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val LOG = logger<SuspendingGitCommitSignatureLoader>()

internal class SuspendingGitCommitSignatureLoader(private val project: Project, parentCs: CoroutineScope) :
  VcsCommitsDataLoader<GitCommitSignature> {
  private val cs = parentCs.childScope(this::class)

  private val semaphore = OverflowSemaphore(overflow = BufferOverflow.DROP_OLDEST)

  override fun loadData(commits: List<CommitId>, onChange: (Map<CommitId, GitCommitSignature>) -> Unit) {
    val commitsByRoot = commits.groupBy({ it.root }, { it.hash }).filter { (root, _) ->
      GitRepositoryManager.getInstance(project).getRepositoryForRootQuick(root) != null
    }
    if (commitsByRoot.isEmpty()) return

    cs.launch {
      semaphore.withPermit {
        for ((root, hashes) in commitsByRoot) {
          runCatching {
            checkCanceled()
            val signatures = withContext(LOADER_CONTEXT) {
              coroutineToIndicator {
                GitCommitSignatureLoader.loadSignatures(project, root, hashes)
              }
            }
            val result = signatures.mapKeys { CommitId(it.key, root) }
            withContext(Dispatchers.UI) {
              checkCanceled()
              onChange(result)
            }
          }.getOrHandleException {
            LOG.info("Failed to load commit signatures", it)
          }
        }
      }
    }
  }

  override fun dispose() {
    cs.cancel("Disposed")
  }
}

// cancelling Git-for-Win process computing the signature sometimes leads to a deadlock and a stuck gpg process
@OptIn(LowLevelLocalMachineAccess::class)
private val LOADER_CONTEXT =
  if (OS.CURRENT == OS.Windows) {
    Dispatchers.IO + NonCancellable
  }
  else {
    Dispatchers.IO
  }
