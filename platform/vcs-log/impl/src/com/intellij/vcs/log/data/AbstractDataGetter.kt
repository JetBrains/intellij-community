// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.data

import com.github.benmanes.caffeine.cache.Caffeine
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.util.Computable
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.CollectConsumer
import com.intellij.util.Consumer
import com.intellij.util.EmptyConsumer
import com.intellij.util.containers.MultiMap
import com.intellij.util.ui.EdtInvocationManager
import com.intellij.util.ui.UIUtil
import com.intellij.vcs.log.VcsLogBundle
import com.intellij.vcs.log.VcsLogProvider
import com.intellij.vcs.log.VcsShortCommitDetails
import com.intellij.vcs.log.data.index.IndexedDetails
import com.intellij.vcs.log.data.index.VcsLogIndex
import com.intellij.vcs.log.util.SequentialLimitedLifoExecutor
import it.unimi.dsi.fastutil.ints.*
import java.awt.EventQueue

/**
 * The DataGetter realizes the following pattern of getting some data (parametrized by `T`) from the VCS:
 *
 *  * it tries to get it from the cache;
 *  * if it fails, it tries to get it from the VCS, and additionally loads several commits around the requested one,
 * to avoid querying the VCS if user investigates details of nearby commits.
 *  * The loading happens asynchronously: a fake [LoadingDetails] object is returned
 *
 * @author Kirill Likhodedov
 */
internal abstract class AbstractDataGetter<T : VcsShortCommitDetails>(protected val storage: VcsLogStorage,
                                                                      private val logProviders: Map<VirtualFile, VcsLogProvider>,
                                                                      protected val index: VcsLogIndex,
                                                                      parentDisposable: Disposable) : Disposable, DataGetter<T> {
  private val cache = Caffeine.newBuilder().maximumSize(10000).build<Int, T>()
  private val loader: SequentialLimitedLifoExecutor<TaskDescriptor> = SequentialLimitedLifoExecutor(this,
                                                                                                    MAX_LOADING_TASKS) { task: TaskDescriptor ->
    preLoadCommitData(task.myCommits, EmptyConsumer.getInstance())
    notifyLoaded()
  }

  /**
   * The sequence number of the current "loading" task.
   */
  private var currentTaskIndex: Long = 0
  private val loadingFinishedListeners: MutableCollection<Runnable> = ArrayList()

  init {
    Disposer.register(parentDisposable, this)
  }

  protected fun notifyLoaded() {
    UIUtil.invokeAndWaitIfNeeded(Runnable {
      for (loadingFinishedListener in loadingFinishedListeners) {
        loadingFinishedListener.run()
      }
    })
  }

  override fun dispose() {
    loadingFinishedListeners.clear()
  }

  override fun getCommitData(hash: Int): T {
    return getCommitData(hash, setOf(hash))
  }

  fun getCommitData(hash: Int, neighbourHashes: Iterable<Int>): T {
    if (!EventQueue.isDispatchThread()) {
      LOG.warn("Accessing AbstractDataGetter from background thread")
      return getFromCache(hash)
             ?: return createPlaceholderCommit(hash, 0 /*not used as this commit is not cached*/)
    }
    val details = getCommitDataIfAvailable(hash)
    if (details != null) return details

    runLoadCommitsData(neighbourHashes)
    // now it is in the cache as "Loading Details" (runLoadCommitsData puts it there)
    return getFromCache(hash)!!
  }

  override fun loadCommitsData(hashes: List<Int>, consumer: Consumer<in List<T>>,
                               errorConsumer: Consumer<in Throwable>, indicator: ProgressIndicator?) {
    LOG.assertTrue(EventQueue.isDispatchThread())
    loadCommitsData(getCommitsMap(hashes), consumer, errorConsumer, indicator)
  }

  private fun loadCommitsData(commits: Int2IntMap,
                              consumer: Consumer<in List<T>>,
                              errorConsumer: Consumer<in Throwable>,
                              indicator: ProgressIndicator?) {
    val result = ArrayList<T>()
    val toLoad = IntOpenHashSet()
    val taskNumber = currentTaskIndex++
    val keyIterator = commits.keys.iterator()
    while (keyIterator.hasNext()) {
      val id = keyIterator.nextInt()
      val details = getCommitDataIfAvailable(id)
      if (details == null || details is LoadingDetails) {
        toLoad.add(id)
        cacheCommit(id, taskNumber)
      }
      else {
        result.add(details)
      }
    }
    if (toLoad.isEmpty()) {
      currentTaskIndex--
      val process = Runnable {
        result.sortedBy { commits[storage.getCommitIndex(it.id, it.root)] }
        consumer.consume(result)
      }
      if (indicator != null) {
        ProgressManager.getInstance().runProcess(process, indicator)
      }
      else {
        process.run()
      }
      return
    }
    val task = object : Task.Backgroundable(null,
                                            VcsLogBundle.message("vcs.log.loading.selected.details.process"),
                                            true, ALWAYS_BACKGROUND) {
      override fun run(indicator: ProgressIndicator) {
        indicator.checkCanceled()
        try {
          preLoadCommitData(toLoad, CollectConsumer(result))
          result.sortedBy { commits[storage.getCommitIndex(it.id, it.root)] }
          notifyLoaded()
        }
        catch (e: VcsException) {
          LOG.warn(e)
          throw RuntimeException(e)
        }
      }

      override fun onSuccess() {
        consumer.consume(result)
      }

      override fun onThrowable(error: Throwable) {
        errorConsumer.consume(error)
      }
    }
    if (indicator != null) {
      ProgressManager.getInstance().runProcessWithProgressAsynchronously(task, indicator)
    }
    else {
      ProgressManager.getInstance().run(task)
    }
  }

  override fun getCommitDataIfAvailable(hash: Int): T? {
    LOG.assertTrue(EventQueue.isDispatchThread())
    val details = getFromCache(hash)
    if (details != null) {
      if (details is LoadingDetailsImpl) {
        if (details.loadingTaskIndex <= currentTaskIndex - MAX_LOADING_TASKS) {
          // don't let old "loading" requests stay in the cache forever
          cache.asMap().remove(hash, details)
          return null
        }
      }
      return details
    }
    return getFromAdditionalCache(hash)
  }

  protected fun getFromCache(hash: Int): T? {
    return cache.getIfPresent(hash)
  }

  /**
   * Lookup somewhere else but the standard cache.
   */
  protected abstract fun getFromAdditionalCache(commitId: Int): T?

  private fun runLoadCommitsData(hashes: Iterable<Int>) {
    val taskNumber = currentTaskIndex++
    val commits = getCommitsMap(hashes)
    val toLoad = IntOpenHashSet()
    val iterator = commits.keys.iterator()
    while (iterator.hasNext()) {
      val id = iterator.nextInt()
      cacheCommit(id, taskNumber)
      toLoad.add(id)
    }
    loader.queue(TaskDescriptor(toLoad))
  }

  private fun cacheCommit(commitId: Int, taskNumber: Long) {
    // fill the cache with temporary "Loading" values to avoid producing queries for each commit that has not been cached yet,
    // even if it will be loaded within a previous query
    if (getFromCache(commitId) == null) {
      cache.put(commitId, createPlaceholderCommit(commitId, taskNumber))
    }
  }

  @Suppress("UNCHECKED_CAST")
  private fun createPlaceholderCommit(commitId: Int, taskNumber: Long): T {
    val dataGetter = index.dataGetter
    return if (dataGetter != null && Registry.`is`("vcs.log.use.indexed.details")) {
      IndexedDetails(dataGetter, storage, commitId, taskNumber) as T
    }
    else {
      LoadingDetailsImpl(Computable { storage.getCommitId(commitId)!! }, taskNumber) as T
    }
  }

  @Throws(VcsException::class)
  protected open fun preLoadCommitData(commits: IntSet, consumer: Consumer<in T>) {
    val rootsAndHashes = MultiMap.create<VirtualFile, String>()
    commits.forEach(IntConsumer { commit: Int ->
      val commitId = storage.getCommitId(commit)
      if (commitId != null) {
        rootsAndHashes.putValue(commitId.root, commitId.hash.asString())
      }
    })
    for ((key, value) in rootsAndHashes.entrySet()) {
      val logProvider = logProviders[key]
      if (logProvider == null) {
        LOG.error("No log provider for root " + key.path + ". All known log providers " + logProviders)
        continue
      }
      readDetails(logProvider, key, ArrayList(value)) { details: T ->
        saveInCache(storage.getCommitIndex(details.id, details.root), details)
        consumer.consume(details)
      }
    }
  }

  @Throws(VcsException::class)
  protected abstract fun readDetails(logProvider: VcsLogProvider,
                                     root: VirtualFile,
                                     hashes: List<String>,
                                     consumer: Consumer<in T>)

  protected fun saveInCache(index: Int, details: T) {
    cache.put(index, details)
  }

  protected fun clear() {
    EdtInvocationManager.invokeAndWaitIfNeeded {
      val iterator: MutableIterator<Map.Entry<Int, T>> = cache.asMap().entries.iterator()
      while (iterator.hasNext()) {
        if (iterator.next().value !is LoadingDetails) {
          iterator.remove()
        }
      }
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

  private class TaskDescriptor(val myCommits: IntSet)

  companion object {
    private val LOG = Logger.getInstance(AbstractDataGetter::class.java)
    private const val MAX_LOADING_TASKS = 10

    private fun getCommitsMap(hashes: Iterable<Int>): Int2IntMap {
      val commits: Int2IntMap = Int2IntOpenHashMap()
      for ((row, commitId) in hashes.withIndex()) {
        commits[commitId] = row
      }
      return commits
    }
  }
}