// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.stash

import com.github.benmanes.caffeine.cache.CacheLoader
import com.github.benmanes.caffeine.cache.Caffeine
import com.google.common.util.concurrent.MoreExecutors
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.LowMemoryWatcher
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.vcs.log.Hash
import git4idea.GitCommit
import git4idea.ui.StashInfo
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException

class GitStashCache(val project: Project) : Disposable {
  private val executor = MoreExecutors.listeningDecorator(AppExecutorUtil.createBoundedApplicationPoolExecutor("Git Stash Loader", 1))

  private val cache = Caffeine.newBuilder()
    .maximumSize(100)
    .executor(executor)
    .buildAsync<StashId, StashData>(CacheLoader { stashId -> doLoadStashData(stashId) })

  init {
    LowMemoryWatcher.register(Runnable { cache.synchronous().invalidateAll() }, this)
  }

  private fun doLoadStashData(stashId: StashId): StashData {
    try {
      LOG.debug("Loading stash at '${stashId.hash}' in '${stashId.root}'")
      val (changes, indexChanges) = GitStashOperations.loadStashChanges(project, stashId.root, stashId.hash, stashId.parentHashes)
      return StashData.Changes(changes, indexChanges)
    }
    catch (e: VcsException) {
      LOG.warn("Could not load stash at '${stashId.hash}' in '${stashId.root}'", e)
      return StashData.Error(e)
    }
    catch (e: Exception) {
      if (e !is ProcessCanceledException) LOG.error("Could not load stash at '${stashId.hash}' in '${stashId.root}'", e)
      throw CompletionException(e);
    }
  }

  fun loadStashData(stashInfo: StashInfo): CompletableFuture<StashData>? {
    if (Disposer.isDisposed(this)) return null

    val commitId = StashId(stashInfo.hash, stashInfo.parentHashes, stashInfo.root)
    val future = cache.get(commitId)
    if (future.isCancelled) return cache.synchronous().refresh(commitId)
    return future
  }

  override fun dispose() {
    executor.shutdown()
    cache.synchronous().invalidateAll()
  }

  companion object {
    private val LOG = Logger.getInstance(GitStashCache::class.java)
  }

  sealed class StashData {
    class Changes(val changes: Collection<Change>, val parentCommits: Collection<GitCommit>) : StashData()
    class Error(val error: VcsException) : StashData()
  }

  private data class StashId(val hash: Hash, val parentHashes: List<Hash>, val root: VirtualFile)
}