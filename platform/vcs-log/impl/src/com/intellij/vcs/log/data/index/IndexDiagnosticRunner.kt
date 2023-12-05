// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.data.index

import com.intellij.concurrency.ConcurrentCollectionFactory
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Attachment
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.progress.util.BackgroundTaskUtil.BackgroundTask
import com.intellij.openapi.progress.util.BackgroundTaskUtil.submitTask
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.vcs.log.data.AbstractDataGetter.Companion.getCommitDetails
import com.intellij.vcs.log.data.CommitDetailsGetter
import com.intellij.vcs.log.data.DataPack
import com.intellij.vcs.log.data.VcsLogStorage
import com.intellij.vcs.log.data.index.IndexDiagnostic.getDiffFor
import com.intellij.vcs.log.data.index.IndexDiagnostic.pickCommits
import com.intellij.vcs.log.data.index.IndexDiagnostic.pickIndexedCommits
import com.intellij.vcs.log.impl.VcsLogErrorHandler
import java.util.concurrent.TimeUnit

internal class IndexDiagnosticRunner(private val index: VcsLogModifiableIndex,
                                     private val storage: VcsLogStorage,
                                     private val roots: Collection<VirtualFile>,
                                     private val dataPackGetter: () -> DataPack,
                                     private val commitDetailsGetter: CommitDetailsGetter,
                                     private val errorHandler: VcsLogErrorHandler,
                                     parent: Disposable) : Disposable {
  private val executor = AppExecutorUtil.createBoundedApplicationPoolExecutor("Index Diagnostic Runner", 1)
  private val bigRepositoriesList = VcsLogBigRepositoriesList.getInstance()
  private val indexingListener = VcsLogIndex.IndexingFinishedListener { root -> runDiagnostic(listOf(root)) }
  private val disposedFlag = Disposer.newCheckedDisposable()
  private val checkedRoots = ConcurrentCollectionFactory.createConcurrentSet<VirtualFile>()

  init {
    index.addListener(indexingListener)
    bigRepositoriesList.addListener(MyBigRepositoriesListListener(), this)
    Disposer.register(parent, this)
    Disposer.register(this, disposedFlag)
  }

  private fun runDiagnostic(rootsToCheck: Collection<VirtualFile>) {
    if (disposedFlag.isDisposed) return

    val backgroundTask = submitTask(executor, this) {
      doRunDiagnostic(rootsToCheck)
    }
    backgroundTask.cancelAfter(3 * 60) {
      thisLogger<IndexDiagnosticRunner>().warn("Index diagnostic for $rootsToCheck is cancelled by timeout")
    }
  }

  @RequiresBackgroundThread
  private fun doRunDiagnostic(rootsToCheck: Collection<VirtualFile>) {
    val dataGetter = index.dataGetter ?: return

    val dataPack = dataPackGetter()
    if (!dataPack.isFull) return

    val uncheckedRoots = rootsToCheck - checkedRoots
    if (uncheckedRoots.isEmpty()) return

    checkedRoots.addAll(uncheckedRoots)

    val oldCommits = dataPack.pickCommits(storage, uncheckedRoots, old = true)
    val newCommits = dataPack.pickCommits(storage, uncheckedRoots, old = false)
    val indexedCommits = dataPack.pickIndexedCommits(dataGetter, uncheckedRoots)
    val commits = (oldCommits + newCommits).filter { !indexedCommits.contains(it) && index.isIndexed(it) } + indexedCommits
    if (commits.isEmpty()) {
      thisLogger().info("Index diagnostic for $uncheckedRoots is skipped as no commits were selected")
      return
    }

    try {
      val commitDetails = commitDetailsGetter.getCommitDetails(commits)
      thisLogger().debug { "Running index diagnostic for commits [${commitDetails.joinToString(separator = " ") { it.id.asString() }}]" }

      val diffReport = dataGetter.getDiffFor(commits, commitDetails, checkAllCommits = false)
      if (diffReport.isNotBlank()) {
        val exception = RuntimeException("Index is corrupted")
        thisLogger().error(exception.message, exception, Attachment("VcsLogIndexDiagnosticReport.txt", diffReport))
        index.markCorrupted()
        errorHandler.handleError(VcsLogErrorHandler.Source.Index, exception)
      }
      else {
        thisLogger().info("Index diagnostic for ${commits.size} commits in $uncheckedRoots is completed")
      }
    }
    catch (e: VcsException) {
      thisLogger().error(e)
    }
    finally {
      (dataGetter.indexStorageBackend as? PhmVcsLogStorageBackend)?.clearCaches()
    }
  }

  fun onDataPackChange() {
    runDiagnostic(roots.filter { root -> index.isIndexed(root) || bigRepositoriesList.isBig(root) })
  }

  override fun dispose() {
    index.removeListener(indexingListener)
    executor.shutdown()
    try {
      executor.awaitTermination(10, TimeUnit.MILLISECONDS)
    }
    catch (_: InterruptedException) {
    }
  }

  private inner class MyBigRepositoriesListListener : VcsLogBigRepositoriesList.Listener {
    override fun onRepositoryAdded(root: VirtualFile) = runDiagnostic(listOf(root))
  }
}

private fun BackgroundTask<*>.cancelAfter(delaySeconds: Long, onCancel: () -> Unit) {
  val cancelTask = {
    this.cancel()
    onCancel()
  }
  val cancelFuture = AppExecutorUtil.getAppScheduledExecutorService().schedule(cancelTask, delaySeconds, TimeUnit.SECONDS)
  this.future.whenComplete { _, _ -> cancelFuture.cancel(false) }
}