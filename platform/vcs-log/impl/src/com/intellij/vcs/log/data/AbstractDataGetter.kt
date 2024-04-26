// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.data

import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.*
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.Consumer
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.vcs.log.*
import it.unimi.dsi.fastutil.ints.Int2ObjectMap
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap
import it.unimi.dsi.fastutil.ints.IntOpenHashSet
import it.unimi.dsi.fastutil.ints.IntSet

abstract class AbstractDataGetter<T : VcsShortCommitDetails> internal constructor(protected val storage: VcsLogStorage,
                                                                                  protected val logProviders: Map<VirtualFile, VcsLogProvider>,
                                                                                  parentDisposable: Disposable) :
  Disposable, DataGetter<T> {
  protected val disposableFlag = Disposer.newCheckedDisposable()

  init {
    Disposer.register(parentDisposable, this)
    Disposer.register(this, disposableFlag)
  }

  override fun loadCommitsData(commits: List<Int>,
                               consumer: Consumer<in List<T>>,
                               errorConsumer: Consumer<in Throwable>,
                               indicator: ProgressIndicator?) {
    val detailsFromCache = getCachedData(commits)
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
            val commitIndex = storage.getCommitIndex(metadata.id, metadata.root)
            saveInCache(commitIndex, metadata)
            detailsFromProvider[commitIndex] = metadata
          }
          val result = commits.mapNotNull { detailsFromCache[it] ?: detailsFromProvider[it] }
          runInEdt(disposableFlag) {
            notifyLoaded()
            consumer.consume(result)
          }
        }
        catch (_: ProcessCanceledException) {
        }
        catch (t: Throwable) {
          if (t !is VcsException) LOG.error(t)
          runInEdt(disposableFlag) { errorConsumer.consume(t) }
        }
      }
    }
    runInBackgroundThread(indicator, task)
  }

  @RequiresBackgroundThread
  @Throws(VcsException::class)
  fun loadCommitsDataSynchronously(commits: Iterable<Int>,
                                   indicator: ProgressIndicator,
                                   consumer: (Int, T) -> Unit) {
    val toLoad = IntOpenHashSet()
    for (id in commits) {
      val details = getCachedData(id)
      if (details == null || details is LoadingDetails) {
        toLoad.add(id)
      }
      else {
        consumer(id, details)
      }
    }
    if (!toLoad.isEmpty()) {
      indicator.checkCanceled()
      doLoadCommitsData(toLoad) { details ->
        val commitIndex = storage.getCommitIndex(details.id, details.root)
        saveInCache(commitIndex, details)
        consumer(commitIndex, details)
      }
      notifyLoaded()
    }
  }

  @RequiresBackgroundThread
  @Throws(VcsException::class)
  protected open fun doLoadCommitsData(commits: IntSet, consumer: Consumer<in T>) {
    val hashesGroupedByRoot = storage.getCommitIds(commits).values
      .groupBy<CommitId, VirtualFile, @NlsSafe String>({ it.root }) { it.hash.asString() }

    for ((root, hashes) in hashesGroupedByRoot) {
      val logProvider = logProviders[root]
      if (logProvider == null) {
        LOG.error("No log provider for root " + root.path + ". All known log providers " + logProviders)
        continue
      }
      doLoadCommitsDataFromProvider(logProvider, root, hashes, consumer)
    }
  }

  protected abstract fun getCachedData(commits: List<Int>): Int2ObjectMap<T>

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

    @RequiresBackgroundThread
    @Throws(VcsException::class)
    @JvmStatic
    fun <T : VcsShortCommitDetails> AbstractDataGetter<T>.getCommitDetails(commits: List<Int>): List<T> {
      val commitToDetailsMap = Int2ObjectOpenHashMap<T>()
      loadCommitsDataSynchronously(commits,
                                   ProgressManager.getGlobalProgressIndicator() ?: EmptyProgressIndicator()) { commitIndex, details ->
        commitToDetailsMap[commitIndex] = details
      }
      return commits.mapNotNull { commitToDetailsMap[it] }
    }

    @RequiresBackgroundThread
    @Throws(VcsException::class)
    @JvmStatic
    fun <T : VcsShortCommitDetails> AbstractDataGetter<T>.getCommitDetails(hash: Hash, root: VirtualFile): T {
      val commitDetailsList = getCommitDetails(listOf(storage.getCommitIndex(hash, root)))
      return commitDetailsList.singleOrNull() ?: throw VcsException(VcsLogBundle.message("vcs.log.failed.loading.details",
                                                                                         hash.asString(), root.name))
    }
  }
}
