// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.data

import com.github.benmanes.caffeine.cache.Caffeine
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.util.BackgroundTaskUtil
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.telemetry.VcsBackendTelemetrySpan.LogData
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.diagnostic.telemetry.TelemetryManager
import com.intellij.platform.diagnostic.telemetry.helpers.use
import com.intellij.platform.vcs.impl.shared.telemetry.VcsScope
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.ui.UIUtil
import com.intellij.vcs.log.*
import com.intellij.vcs.log.graph.impl.facade.PermanentGraphImpl
import com.intellij.vcs.log.util.SequentialLimitedLifoExecutor
import org.jetbrains.annotations.CalledInAny
import java.awt.EventQueue
import java.util.function.Predicate

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
  private val conditionsCache = CurrentBranchConditionCache(logData, parentDisposable)
  private var currentBranchesChecksum = 0

  init {
    taskExecutor = SequentialLimitedLifoExecutor(parentDisposable, 10, CachingTask::run)
    logData.addDataPackChangeListener {
      val checksum = logData.dataPack.refsModel.branches.hashCode()
      if (currentBranchesChecksum != checksum) { // clear cache if branches set changed after refresh
        clearCache()
      }
      //do not cache transient small data pack branches checksum as it will be substituted by regular data pack
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

  @CalledInAny
  fun getContainingBranchesQuickly(root: VirtualFile, hash: Hash): List<String>? {
    val cachedBranches = cache.getIfPresent(CommitId(hash, root))
    if (cachedBranches != null) return cachedBranches

    val dataPack = logData.dataPack
    val commitIndex = logData.getCommitIndex(hash, root)
    val pg = dataPack.permanentGraph
    if (pg is PermanentGraphImpl<Int>) {
      val nodeId = pg.permanentCommitsInfo.getNodeId(commitIndex)
      if (nodeId < 10000 && canUseGraphForComputation(logData.getLogProvider(root))) {
        return getContainingBranchesSynchronously(dataPack, root, hash)
      }
    }
    return BackgroundTaskUtil.tryComputeFast({
                                               return@tryComputeFast getContainingBranchesSynchronously(dataPack, root, hash)
                                             }, 100)
  }

  @CalledInAny
  fun getContainedInCurrentBranchCondition(root: VirtualFile): Predicate<Int> =
    conditionsCache.getContainedInCurrentBranchCondition(root)

  @CalledInAny
  fun getContainingBranchesSynchronously(root: VirtualFile, hash: Hash): List<String> {
    return getContainingBranchesSynchronously(logData.dataPack, root, hash)
  }

  @CalledInAny
  private fun getContainingBranchesSynchronously(dataPack: DataPack, root: VirtualFile, hash: Hash): List<String> {
    return CachingTask(createTask(root, hash, dataPack), dataPack.refsModel.branches.hashCode()).run()
  }

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
      return TelemetryManager.getInstance().getTracer(VcsScope)
        .spanBuilder(LogData.GettingContainingBranches.getName())
        .use {
          try {
            getContainingBranches(myProvider, myRoot, myHash)
          }
          catch (e: VcsException) {
            LOG.warn(e)
            emptyList()
          }
        }
    }

    @Throws(VcsException::class)
    protected abstract fun getContainingBranches(provider: VcsLogProvider,
                                                 root: VirtualFile, hash: Hash): List<String>
  }

  private inner class GraphTask(provider: VcsLogProvider, root: VirtualFile, hash: Hash, dataPack: DataPack) :
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
    fun run(): List<String> {
      val branches = delegate.getContainingBranches()
      val commitId = CommitId(delegate.myHash, delegate.myRoot)
      UIUtil.invokeLaterIfNeeded {
        cache(commitId, branches, branchesChecksum)
      }
      return branches
    }
  }

  companion object {
    private val LOG = Logger.getInstance(ContainingBranchesGetter::class.java)

    private fun canUseGraphForComputation(logProvider: VcsLogProvider) =
      VcsLogProperties.LIGHTWEIGHT_BRANCHES.getOrDefault(logProvider)
  }
}
