// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.stash

import com.github.benmanes.caffeine.cache.AsyncCacheLoader
import com.github.benmanes.caffeine.cache.Caffeine
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.util.BackgroundTaskUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Computable
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.vcs.log.Hash
import git4idea.GitCommit
import git4idea.ui.StashInfo
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException
import java.util.concurrent.TimeUnit

@Service(Service.Level.PROJECT)
class GitStashCache(val project: Project) : Disposable {
  private val disposableFlag = Disposer.newCheckedDisposable()
  private val executor = AppExecutorUtil.createBoundedApplicationPoolExecutor("Git Stash Loader", 1)
  private val cache = Caffeine.newBuilder()
    .executor(executor)
    .buildAsync<StashId, StashData>(AsyncCacheLoader { stashId, executor ->
      BackgroundTaskUtil.submitTask(executor, this@GitStashCache, Computable { doLoadStashData(stashId) }).future
    })

  private val stashTracker get() = project.service<GitStashTracker>()

  init {
    Disposer.register(this, disposableFlag)
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
      throw CompletionException(e)
    }
  }

  fun loadStashData(stashInfo: StashInfo) = getFutureStashData(stashInfo, false)
  fun getCachedData(stashInfo: StashInfo): StashData.Changes? {
    return getFutureStashData(stashInfo, true)?.getNowSafely() as? StashData.Changes
  }

  private fun getFutureStashData(stashInfo: StashInfo, cached: Boolean): CompletableFuture<StashData>? {
    val id = stashInfo.stashId()
    return if (cached) cache.getIfPresent(id) else loadFutureStashData(id)
  }

  private fun loadFutureStashData(id: StashId): CompletableFuture<StashData>? {
    if (disposableFlag.isDisposed) return null
    val future = cache.get(id)
    return if (future.isCancelled) cache.synchronous().refresh(id) else future
  }

  @RequiresEdt
  internal fun preloadStashes() {
    val currentStashes = stashTracker.allStashes().map { it.stashId() }.toSet()
    val previousStashes = cache.synchronous().asMap().keys

    cache.synchronous().invalidateAll(previousStashes - currentStashes)
    currentStashes.forEach { loadFutureStashData(it) }
  }

  private fun StashInfo.stashId() = StashId(hash, parentHashes, root)

  internal fun clear() {
    cache.synchronous().invalidateAll()
  }

  override fun dispose() {
    executor.shutdown()
    try {
      executor.awaitTermination(10, TimeUnit.MILLISECONDS)
    }
    finally {
    }
    clear()
  }

  companion object {
    private val LOG = Logger.getInstance(GitStashCache::class.java)

    private fun <T> CompletableFuture<T>.getNowSafely(): T? {
      return try {
        getNow(null)
      }
      catch (e: Throwable) {
        null
      }
    }
  }

  sealed class StashData {
    class Changes(val changes: Collection<Change>, val parentCommits: Collection<GitCommit>) : StashData()
    class Error(val error: VcsException) : StashData()
  }

  private data class StashId(val hash: Hash, val parentHashes: List<Hash>, val root: VirtualFile)
}