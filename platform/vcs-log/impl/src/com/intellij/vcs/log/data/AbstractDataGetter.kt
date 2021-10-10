// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.data

import com.github.benmanes.caffeine.cache.Caffeine
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.util.Computable
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.Consumer
import com.intellij.util.EmptyConsumer
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.ui.UIUtil
import com.intellij.vcs.log.*
import com.intellij.vcs.log.data.index.IndexedDetails
import com.intellij.vcs.log.data.index.VcsLogIndex
import com.intellij.vcs.log.util.SequentialLimitedLifoExecutor
import it.unimi.dsi.fastutil.ints.*
import java.awt.EventQueue

abstract class AbstractDataGetter<T : VcsShortCommitDetails> internal constructor(protected val storage: VcsLogStorage,
                                                                                  protected val logProviders: Map<VirtualFile, VcsLogProvider>,
                                                                                  parentDisposable: Disposable) :
  Disposable, DataGetter<T> {

  init {
    Disposer.register(parentDisposable, this)
  }

  override fun loadCommitsData(commits: List<Int>,
                               consumer: Consumer<in List<T>>,
                               errorConsumer: Consumer<in Throwable>,
                               indicator: ProgressIndicator?) {
    val detailsFromCache = getCommitDataIfAvailable(commits)
    if (detailsFromCache.size == commits.size) {
      // client of this code expect start/stop methods to get called for the provided indicator
      runInCurrentThread(indicator) {
        consumer.consume(commits.mapNotNull { detailsFromCache[it] })
      }
      return
    }

    val toLoad = IntOpenHashSet(commits).apply { removeAll(detailsFromCache.keys) }
    cacheCommits(toLoad)

    val task = object : Task.Backgroundable(null,
                                            VcsLogBundle.message("vcs.log.loading.selected.details.process"),
                                            true, ALWAYS_BACKGROUND) {
      override fun run(indicator: ProgressIndicator) {
        indicator.checkCanceled()
        try {
          val detailsFromProvider = Int2ObjectOpenHashMap<T>()
          doLoadCommitsData(toLoad) { metadata ->
            detailsFromProvider[storage.getCommitIndex(metadata.id, metadata.root)] = metadata
          }
          val result = commits.mapNotNull { detailsFromCache[it] ?: detailsFromProvider[it] }
          notifyLoaded()
          runInEdt(this@AbstractDataGetter) {
            consumer.consume(result)
          }
        }
        catch (_: ProcessCanceledException) {
        }
        catch (t: Throwable) {
          if (t !is VcsException) LOG.error(t)
          runInEdt(this@AbstractDataGetter) { errorConsumer.consume(t) }
        }
      }
    }
    runInBackgroundThread(indicator, task)
  }

  @RequiresBackgroundThread
  @Throws(VcsException::class)
  protected open fun doLoadCommitsData(commits: IntSet, consumer: Consumer<in T>) {
    val hashesGroupedByRoot = commits.mapNotNull { storage.getCommitId(it) }
      .groupBy<CommitId, VirtualFile, @NlsSafe String>({ it.root }) { it.hash.asString() }

    for ((root, hashes) in hashesGroupedByRoot) {
      val logProvider = logProviders[root]
      if (logProvider == null) {
        LOG.error("No log provider for root " + root.path + ". All known log providers " + logProviders)
        continue
      }
      doLoadCommitsDataFromProvider(logProvider, root, hashes) { details: T ->
        saveInCache(storage.getCommitIndex(details.id, details.root), details)
        consumer.consume(details)
      }
    }
  }

  protected abstract fun getCommitDataIfAvailable(commits: List<Int>): Int2ObjectMap<T>

  protected abstract fun saveInCache(commit: Int, details: T)

  protected open fun cacheCommits(commits: IntOpenHashSet) = Unit

  @RequiresBackgroundThread
  @Throws(VcsException::class)
  protected abstract fun doLoadCommitsDataFromProvider(logProvider: VcsLogProvider,
                                                       root: VirtualFile,
                                                       hashes: List<String>,
                                                       consumer: Consumer<in T>)

  protected open fun notifyLoaded() = Unit

  companion object {
    private val LOG = Logger.getInstance(AbstractDataGetter::class.java)

    private fun runInCurrentThread(indicator: ProgressIndicator?, runnable: () -> Unit) {
      if (indicator != null) {
        ProgressManager.getInstance().runProcess(runnable, indicator)
      }
      else {
        runnable.invoke()
      }
    }

    private fun runInBackgroundThread(indicator: ProgressIndicator?, task: Task.Backgroundable) {
      if (indicator != null) {
        ProgressManager.getInstance().runProcessWithProgressAsynchronously(task, indicator)
      }
      else {
        ProgressManager.getInstance().run(task)
      }
    }
  }
}

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
abstract class AbstractDataGetterWithSequentialLoader<T : VcsShortCommitDetails>(storage: VcsLogStorage,
                                                                                 logProviders: Map<VirtualFile, VcsLogProvider>,
                                                                                 protected val index: VcsLogIndex,
                                                                                 parentDisposable: Disposable) :
  AbstractDataGetter<T>(storage, logProviders, parentDisposable) {

  private val cache = Caffeine.newBuilder().maximumSize(10000).build<Int, T>()
  private val loader: SequentialLimitedLifoExecutor<TaskDescriptor> = SequentialLimitedLifoExecutor(this,
                                                                                                    MAX_LOADING_TASKS) { task: TaskDescriptor ->
    doLoadCommitsData(task.myCommits, EmptyConsumer.getInstance())
    notifyLoaded()
  }

  /**
   * The sequence number of the current "loading" task.
   */
  private var currentTaskIndex: Long = 0
  private val loadingFinishedListeners: MutableCollection<Runnable> = ArrayList()

  override fun getCommitData(commit: Int): T {
    return getCommitData(commit, setOf(commit))
  }

  fun getCommitData(commit: Int, neighbourHashes: Iterable<Int>): T {
    if (!EventQueue.isDispatchThread()) {
      LOG.warn("Accessing AbstractDataGetter from background thread")
      return getFromCache(commit)
             ?: return createPlaceholderCommit(commit, 0 /*not used as this commit is not cached*/)
    }
    val details = getCommitDataIfAvailable(commit)
    if (details != null) return details

    val toLoad = IntOpenHashSet(neighbourHashes.iterator())
    val taskNumber = currentTaskIndex++
    toLoad.forEach(IntConsumer { cacheCommit(it, taskNumber) })
    loader.queue(TaskDescriptor(toLoad))
    // now it is in the cache as "Loading Details" (cacheCommit puts it there)
    return getFromCache(commit)!!
  }

  override fun getCommitDataIfAvailable(commit: Int): T? {
    LOG.assertTrue(EventQueue.isDispatchThread())
    val details = getFromCache(commit)
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
    return getFromAdditionalCache(commit)
  }

  override fun getCommitDataIfAvailable(commits: List<Int>): Int2ObjectMap<T> {
    val detailsFromCache = commits.associateNotNull {
      val details = getCommitDataIfAvailable(it)
      if (details is LoadingDetails) {
        return@associateNotNull null
      }
      details
    }
    return detailsFromCache
  }

  protected fun getFromCache(commit: Int): T? = cache.getIfPresent(commit)
  override fun saveInCache(commit: Int, details: T) = cache.put(commit, details)
  abstract fun getFromAdditionalCache(commit: Int): T?

  private fun cacheCommit(commit: Int, taskNumber: Long) {
    // fill the cache with temporary "Loading" values to avoid producing queries for each commit that has not been cached yet,
    // even if it will be loaded within a previous query
    if (getFromCache(commit) == null) {
      cache.put(commit, createPlaceholderCommit(commit, taskNumber))
    }
  }

  override fun cacheCommits(commits: IntOpenHashSet) {
    val taskNumber = currentTaskIndex++
    commits.forEach(IntConsumer { commit -> cacheCommit(commit, taskNumber) })
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
    UIUtil.invokeAndWaitIfNeeded(Runnable {
      for (loadingFinishedListener in loadingFinishedListeners) {
        loadingFinishedListener.run()
      }
    })
  }

  override fun dispose() {
    loadingFinishedListeners.clear()
  }

  private class TaskDescriptor(val myCommits: IntSet)

  companion object {
    private val LOG = Logger.getInstance(AbstractDataGetterWithSequentialLoader::class.java)
    private const val MAX_LOADING_TASKS = 10

    private inline fun <V> Iterable<Int>.associateNotNull(transform: (Int) -> V?): Int2ObjectMap<V> {
      val result = Int2ObjectOpenHashMap<V>()
      for (element in this) {
        val value = transform(element) ?: continue
        result[element] = value
      }
      return result
    }
  }
}