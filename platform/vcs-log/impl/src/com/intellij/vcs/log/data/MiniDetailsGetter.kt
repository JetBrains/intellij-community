// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.data

import com.github.benmanes.caffeine.cache.Caffeine
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.Consumer
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.vcs.log.VcsCommitMetadata
import com.intellij.vcs.log.VcsLogObjectsFactory
import com.intellij.vcs.log.VcsLogProvider
import com.intellij.vcs.log.data.index.IndexedDetails
import com.intellij.vcs.log.data.index.VcsLogIndex
import com.intellij.vcs.log.runInEdt
import com.intellij.vcs.log.util.SequentialLimitedLifoExecutor
import it.unimi.dsi.fastutil.ints.*
import org.jetbrains.annotations.ApiStatus
import java.awt.EventQueue

class MiniDetailsGetter internal constructor(project: Project,
                                             storage: VcsLogStorage,
                                             logProviders: Map<VirtualFile, VcsLogProvider>,
                                             private val topCommitsDetailsCache: TopCommitsCache,
                                             private val index: VcsLogIndex,
                                             parentDisposable: Disposable) :
  AbstractDataGetter<VcsCommitMetadata>(storage, logProviders, parentDisposable) {

  private val factory = project.getService(VcsLogObjectsFactory::class.java)
  private val cache = Caffeine.newBuilder().maximumSize(10000).build<Int, VcsCommitMetadata>()
  private val loader = SequentialLimitedLifoExecutor(this, MAX_LOADING_TASKS) { task: TaskDescriptor ->
    doLoadCommitsData(task.commits) { commitId, data -> saveInCache(commitId, data) }
    notifyLoaded()
  }

  /**
   * The sequence number of the current "loading" task.
   */
  private var currentTaskIndex: Long = 0
  private val loadingFinishedListeners = ArrayList<Runnable>()

  fun getCachedDataOrPlaceholder(commit: Int): VcsCommitMetadata {
    return getCommitData(commit, emptySet())
  }

  @ApiStatus.Internal
  fun getCommitData(commit: Int, commitsToLoad: Iterable<Int>): VcsCommitMetadata {
    val details = getFromCacheAndCleanOldPlaceholder(commit)
    if (details != null) return details

    if (!EventQueue.isDispatchThread()) {
      thisLogger().assertTrue(commitsToLoad.none(), "Requesting loading commits in background thread is not supported.")
      return createPlaceholderCommit(commit, 0 /*not used as this commit is not cached*/)
    }

    val toLoad = IntOpenHashSet(commitsToLoad.iterator())
    if (toLoad.isEmpty()) {
      return cache.getIfPresent(commit) ?: createPlaceholderCommit(commit, 0 /*not used as this commit is not cached*/)
    }

    val taskNumber = currentTaskIndex++
    toLoad.forEach(IntConsumer { cacheCommit(it, taskNumber) })
    loader.queue(TaskDescriptor(toLoad))
    return cache.getIfPresent(commit) ?: createPlaceholderCommit(commit, taskNumber)
  }

  private fun getFromCacheAndCleanOldPlaceholder(commit: Int): VcsCommitMetadata? {
    if (!EventQueue.isDispatchThread()) {
      return cache.getIfPresent(commit) ?: topCommitsDetailsCache[commit]
    }
    val details = cache.getIfPresent(commit)
    if (details != null) {
      if (details is LoadingDetailsImpl) {
        if (details.loadingTaskIndex <= currentTaskIndex - MAX_LOADING_TASKS) {
          // don't let old "loading" requests stay in the cache forever
          cache.asMap().remove(commit, details)
          return null
        }
      }
      return details
    }
    return topCommitsDetailsCache[commit]
  }

  override fun getCachedData(commit: Int): VcsCommitMetadata? {
    return cache.getIfPresent(commit).takeIf { it !is LoadingDetails } ?: topCommitsDetailsCache[commit]
  }

  override fun getCachedData(commits: List<Int>): Int2ObjectMap<VcsCommitMetadata> {
    return commits.associateNotNull { getCachedData(it) }
  }

  override fun saveInCache(commit: Int, details: VcsCommitMetadata) = cache.put(commit, details)

  @RequiresEdt
  private fun cacheCommit(commitId: Int, taskNumber: Long) {
    // fill the cache with temporary "Loading" values to avoid producing queries for each commit that has not been cached yet,
    // even if it will be loaded within a previous query
    if (cache.getIfPresent(commitId) == null) {
      cache.put(commitId, createPlaceholderCommit(commitId, taskNumber))
    }
  }

  @RequiresEdt
  override fun cacheCommits(commits: IntOpenHashSet) {
    val taskNumber = currentTaskIndex++
    commits.forEach(IntConsumer { commit -> cacheCommit(commit, taskNumber) })
  }

  @RequiresBackgroundThread
  @Throws(VcsException::class)
  override fun doLoadCommitsData(commits: IntSet, consumer: Consumer<in VcsCommitMetadata>) {
    doLoadCommitsData(commits) { _, metadata -> consumer.consume(metadata) }
  }

  @RequiresBackgroundThread
  @Throws(VcsException::class)
  fun doLoadCommitsData(commits: IntSet, consumer: (Int, VcsCommitMetadata) -> Unit) {
    val dataGetter = index.dataGetter
    if (dataGetter == null) {
      super.doLoadCommitsData(commits) { data -> consumer(storage.getCommitIndex(data.id, data.root), data) }
      return
    }

    val metadata = IndexedDetails.createMetadata(commits, dataGetter, storage, factory)
    metadata.forEach { (commit, metadata) -> consumer(commit, metadata) }
    val notIndexed = commits - metadata.keys

    if (!notIndexed.isEmpty()) {
      super.doLoadCommitsData(notIndexed) { data -> consumer(storage.getCommitIndex(data.id, data.root), data) }
    }
  }

  @RequiresBackgroundThread
  @Throws(VcsException::class)
  override fun doLoadCommitsDataFromProvider(logProvider: VcsLogProvider,
                                             root: VirtualFile,
                                             hashes: List<String>,
                                             consumer: Consumer<in VcsCommitMetadata>) {
    logProvider.readMetadata(root, hashes, consumer)
  }

  private fun createPlaceholderCommit(commit: Int, taskNumber: Long): VcsCommitMetadata {
    val dataGetter = index.dataGetter
    return if (dataGetter != null && Registry.`is`("vcs.log.use.indexed.details")) {
      IndexedDetails(dataGetter, storage, commit, taskNumber)
    }
    else {
      logProviders.keys.singleOrNull()?.let {
        LoadingDetailsWithRoot(storage, commit, it, taskNumber)
      } ?: LoadingDetailsImpl(storage, commit, taskNumber)
    }
  }

  /**
   * This listener will be notified when any details loading process finishes.
   * The notification will happen in the EDT.
   */
  fun addDetailsLoadedListener(runnable: Runnable) {
    loadingFinishedListeners.add(runnable)
  }

  fun removeDetailsLoadedListener(runnable: Runnable) {
    loadingFinishedListeners.remove(runnable)
  }

  override fun notifyLoaded() {
    runInEdt(disposableFlag) {
      for (loadingFinishedListener in loadingFinishedListeners) {
        loadingFinishedListener.run()
      }
    }
  }

  override fun dispose() {
    loadingFinishedListeners.clear()
  }

  private class TaskDescriptor(val commits: IntSet)

  companion object {
    private const val MAX_LOADING_TASKS = 10

    private inline fun <V> Iterable<Int>.associateNotNull(transform: (Int) -> V?): Int2ObjectMap<V> {
      val result = Int2ObjectOpenHashMap<V>()
      for (element in this) {
        val value = transform(element) ?: continue
        result[element] = value
      }
      return result
    }

    private operator fun IntSet.minus(other: IntSet): IntSet {
      if (other.isEmpty()) return this
      return filterNotTo(IntOpenHashSet()) { it in other }
    }
  }
}
