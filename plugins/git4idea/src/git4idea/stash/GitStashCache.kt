// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.stash

import com.google.common.cache.CacheBuilder
import com.google.common.cache.CacheLoader
import com.google.common.cache.LoadingCache
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.LowMemoryWatcher
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.concurrency.EdtExecutorService
import com.intellij.util.messages.Topic
import com.intellij.vcs.log.CommitId
import com.intellij.vcs.log.Hash
import git4idea.ui.StashInfo
import java.util.*
import java.util.concurrent.Callable

class GitStashCache(val project: Project) : Disposable {
  private val executor = MoreExecutors.listeningDecorator(AppExecutorUtil.createBoundedApplicationPoolExecutor("Git Stash Loader", 1))

  val cache: LoadingCache<CommitId, ListenableFuture<StashData>> = CacheBuilder.newBuilder()
    .maximumSize(100).build(CacheLoader.from { commitId -> return@from scheduleLoading(commitId!!) })

  init {
    LowMemoryWatcher.register(Runnable { cache.invalidateAll() }, this)
  }

  private fun scheduleLoading(commitId: CommitId): ListenableFuture<StashData>? {
    val future = executor.submit(Callable {
      try {
        LOG.debug("Loading stash at '${commitId.hash}' in '${commitId.root}'")
        return@Callable StashData.ChangeList(GitStashOperations.loadStashedChanges(project, commitId.root, commitId.hash))
      }
      catch (e: VcsException) {
        LOG.warn("Could not load stash at '${commitId.hash}' in '${commitId.root}'", e)
        return@Callable StashData.Error(e)
      }
    })
    future.addListener(Runnable {
      project.messageBus.syncPublisher(GIT_STASH_LOADED).stashLoaded(commitId.root, commitId.hash)
    }, EdtExecutorService.getInstance())
    return future
  }

  fun getCachedStashData(stashInfo: StashInfo): StashData? {
    val future = cache.getIfPresent(CommitId(stashInfo.hash, stashInfo.root)) ?: return null
    if (!future.isDone) return null
    return future.get()
  }

  fun loadStashData(stashInfo: StashInfo) {
    if (Disposer.isDisposed(this)) return
    cache.get(CommitId(stashInfo.hash, stashInfo.root))
  }

  override fun dispose() {
    executor.shutdown()
    cache.invalidateAll()
  }

  companion object {
    private val LOG = Logger.getInstance(GitStashCache::class.java)
    val GIT_STASH_LOADED = Topic.create("Git Stash Loaded", StashLoadedListener::class.java)
  }

  sealed class StashData {
    class ChangeList(val changeList: CommittedChangeList): StashData()
    class Error(val error: VcsException): StashData()
  }

  interface StashLoadedListener : EventListener {
    fun stashLoaded(root: VirtualFile, hash: Hash)
  }
}