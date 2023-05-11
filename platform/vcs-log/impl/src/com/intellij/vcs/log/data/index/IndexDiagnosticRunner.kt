// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.data.index

import com.intellij.concurrency.ConcurrentCollectionFactory
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Attachment
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.containers.HashingStrategy
import com.intellij.vcs.log.data.AbstractDataGetter.Companion.getCommitDetails
import com.intellij.vcs.log.data.CommitDetailsGetter
import com.intellij.vcs.log.data.DataPack
import com.intellij.vcs.log.data.VcsLogStorage
import com.intellij.vcs.log.data.index.IndexDiagnostic.getDiffFor
import com.intellij.vcs.log.data.index.IndexDiagnostic.getFirstCommits
import com.intellij.vcs.log.impl.VcsLogErrorHandler

internal class IndexDiagnosticRunner(private val index: VcsLogModifiableIndex,
                                     private val storage: VcsLogStorage,
                                     private val roots: Collection<VirtualFile>,
                                     private val dataPackGetter: () -> DataPack,
                                     private val commitDetailsGetter: CommitDetailsGetter,
                                     private val errorHandler: VcsLogErrorHandler,
                                     parent: Disposable) : Disposable {
  private val indexingListener = VcsLogIndex.IndexingFinishedListener { root -> runDiagnostic(listOf(root)) }
  private val checkedRoots = ConcurrentCollectionFactory.createConcurrentSet<VirtualFile>()

  init {
    index.addListener(indexingListener)
    Disposer.register(parent, this)
  }

  @RequiresBackgroundThread
  private fun runDiagnostic(rootsToCheck: Collection<VirtualFile>) {
    val dataGetter = index.dataGetter ?: return

    val dataPack = dataPackGetter()
    if (!dataPack.isFull) return

    val uncheckedRoots = rootsToCheck - checkedRoots
    if (uncheckedRoots.isEmpty()) return

    thisLogger().info("Running index diagnostic for $uncheckedRoots")
    checkedRoots.addAll(uncheckedRoots)

    val commits = dataPack.getFirstCommits(storage, uncheckedRoots)
    try {
      val commitDetails = commitDetailsGetter.getCommitDetails(commits)
      val diffReport = dataGetter.getDiffFor(commits, commitDetails)
      if (diffReport.isNotBlank()) {
        val exception = RuntimeException("Index is corrupted")
        thisLogger().error(exception.message, exception, Attachment("VcsLogIndexDiagnosticReport.txt", diffReport))
        index.markCorrupted()
        errorHandler.handleError(VcsLogErrorHandler.Source.Index, exception)
      }
    }
    catch (e: VcsException) {
      thisLogger().error(e)
    }
  }

  fun onDataPackChange() {
    runDiagnostic(roots.filter(index::isIndexed))
  }

  override fun dispose() {
    index.removeListener(indexingListener)
  }
}