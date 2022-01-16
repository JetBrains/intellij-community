// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.data

import com.github.benmanes.caffeine.cache.Caffeine
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.util.BackgroundTaskUtil
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.vcs.log.*
import com.intellij.vcs.log.graph.impl.facade.PermanentGraphImpl
import com.intellij.vcs.log.util.SequentialLimitedLifoExecutor
import com.intellij.vcs.log.util.StopWatch
import org.jetbrains.annotations.CalledInAny
import java.awt.EventQueue

/**
 * Provides capabilities to asynchronously calculate "contained in branches" information.
 */
class ContainingBranchesGetter internal constructor(private val logData: VcsLogData, parentDisposable: Disposable) {
  private val taskExecutor: SequentialLimitedLifoExecutor<CachingTask>

  // other fields accessed only from EDT
  private val loadingFinishedListeners: MutableList<Runnable> = ArrayList()
  private val cache = Caffeine.newBuilder()
    .maximumSize(2000)
    .build<CommitId, List<String>>()
  private val conditionsCache: CurrentBranchConditionCache
  private var currentBranchesChecksum = 0

  init {
    conditionsCache = CurrentBranchConditionCache(logData, parentDisposable)
    taskExecutor = SequentialLimitedLifoExecutor(parentDisposable, 10, CachingTask::run)
    logData.addDataPackChangeListener { dataPack: DataPack ->
      val currentBranches = dataPack.refsModel.branches
      val checksum = currentBranches.hashCode()
      if (currentBranchesChecksum != 0 && currentBranchesChecksum != checksum) { // clear cache if branches set changed after refresh
        clearCache()
      }
      currentBranchesChecksum = checksum
    }
  }

  @RequiresEdt
  private fun cache(commitId: CommitId, branches: List<String>, branchesChecksum: Int) {
    if (branchesChecksum == currentBranchesChecksum) {
      cache.put(commitId, branches)
      notifyListeners()
    }
  }

  @RequiresEdt
  private fun clearCache() {
    cache.invalidateAll()
    taskExecutor.clear()
    conditionsCache.clear()
    // re-request containing branches information for the commit user (possibly) currently stays on
    ApplicationManager.getApplication().invokeLater { notifyListeners() }
  }

  /**
   * This task will be executed each time the calculating process completes.
   */
  fun addTaskCompletedListener(runnable: Runnable) {
    LOG.assertTrue(EventQueue.isDispatchThread())
    loadingFinishedListeners.add(runnable)
  }

  fun removeTaskCompletedListener(runnable: Runnable) {
    LOG.assertTrue(EventQueue.isDispatchThread())
    loadingFinishedListeners.remove(runnable)
  }

  private fun notifyListeners() {
    LOG.assertTrue(EventQueue.isDispatchThread())
    for (listener in loadingFinishedListeners) {
      listener.run()
    }
  }

  /**
   * Returns the alphabetically sorted list of branches containing the specified node, if this information is ready;
   * if it is not available, starts calculating in the background and returns null.
   */
  fun requestContainingBranches(root: VirtualFile, hash: Hash): List<String>? {
    LOG.assertTrue(EventQueue.isDispatchThread())
    val refs = getContainingBranchesFromCache(root, hash)
    if (refs == null) {
      taskExecutor.queue(CachingTask(createTask(root, hash, logData.dataPack), currentBranchesChecksum))
    }
    return refs
  }

  fun getContainingBranchesFromCache(root: VirtualFile, hash: Hash): List<String>? {
    LOG.assertTrue(EventQueue.isDispatchThread())
    return cache.getIfPresent(CommitId(hash, root))
  }

  fun getContainingBranchesQuickly(root: VirtualFile, hash: Hash): List<String>? {
    LOG.assertTrue(EventQueue.isDispatchThread())
    val commitId = CommitId(hash, root)
    var branches = cache.getIfPresent(commitId)
    if (branches == null) {
      val index = logData.getCommitIndex(hash, root)
      val pg = logData.dataPack.permanentGraph
      if (pg is PermanentGraphImpl<Int>) {
        val nodeId = pg.permanentCommitsInfo.getNodeId(index)
        branches = if (nodeId < 10000 && canUseGraphForComputation(logData.getLogProvider(root))) {
          getContainingBranchesSynchronously(root, hash)
        }
        else {
          BackgroundTaskUtil.tryComputeFast({ getContainingBranchesSynchronously(root, hash) }, 100)
        }
        if (branches != null) cache.put(commitId, branches)
      }
    }
    return branches
  }

  @CalledInAny
  fun getContainedInCurrentBranchCondition(root: VirtualFile) =
    conditionsCache.getContainedInCurrentBranchCondition(root)

  @CalledInAny
  fun getContainingBranchesSynchronously(root: VirtualFile, hash: Hash) =
    createTask(root, hash, logData.dataPack).getContainingBranches()

  private fun createTask(root: VirtualFile, hash: Hash, dataPack: DataPack): Task {
    val provider = logData.getLogProvider(root)
    return if (canUseGraphForComputation(provider)) {
      GraphTask(provider, root, hash, dataPack)
    }
    else ProviderTask(provider, root, hash)
  }

  private abstract class Task(private val myProvider: VcsLogProvider, val myRoot: VirtualFile, val myHash: Hash) {

    @Throws(VcsException::class)
    fun getContainingBranches(): List<String> {
      val sw = StopWatch.start("get containing branches")
      return try {
        getContainingBranches(myProvider, myRoot, myHash)
      }
      catch (e: VcsException) {
        LOG.warn(e)
        emptyList()
      }
      finally {
        sw.report()
      }
    }

    @Throws(VcsException::class)
    protected abstract fun getContainingBranches(provider: VcsLogProvider,
                                                 root: VirtualFile, hash: Hash): List<String>
  }

  private inner class GraphTask constructor(provider: VcsLogProvider, root: VirtualFile, hash: Hash, dataPack: DataPack) :
    Task(provider, root, hash) {

    private val graph = dataPack.permanentGraph
    private val refs = dataPack.refsModel

    override fun getContainingBranches(provider: VcsLogProvider, root: VirtualFile, hash: Hash): List<String> {
      val commitIndex = logData.getCommitIndex(hash, root)
      return graph.getContainingBranches(commitIndex)
        .map(::getBranchesRefs)
        .flatten()
        .sortedWith(provider.referenceManager.labelsOrderComparator)
        .map(VcsRef::getName)
    }

    private fun getBranchesRefs(branchIndex: Int) =
      refs.refsToCommit(branchIndex).filter { it.type.isBranch }
  }

  private class ProviderTask(provider: VcsLogProvider, root: VirtualFile, hash: Hash) : Task(provider, root, hash) {
    @Throws(VcsException::class)
    override fun getContainingBranches(provider: VcsLogProvider,
                                       root: VirtualFile, hash: Hash) =
      provider.getContainingBranches(root, hash).sorted()
  }

  private inner class CachingTask(private val delegate: Task, private val branchesChecksum: Int) {
    fun run() {
      val branches = delegate.getContainingBranches()
      val commitId = CommitId(delegate.myHash, delegate.myRoot)
      ApplicationManager.getApplication().invokeLater {
        cache(commitId, branches, branchesChecksum)
      }
    }
  }

  companion object {
    private val LOG = Logger.getInstance(ContainingBranchesGetter::class.java)

    private fun canUseGraphForComputation(logProvider: VcsLogProvider) =
      VcsLogProperties.LIGHTWEIGHT_BRANCHES.getOrDefault(logProvider)
  }
}