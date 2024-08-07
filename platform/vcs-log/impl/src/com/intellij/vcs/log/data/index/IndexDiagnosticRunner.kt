// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Internal

package com.intellij.vcs.log.data.index

import com.intellij.concurrency.ConcurrentCollectionFactory
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Attachment
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.vcs.log.data.*
import com.intellij.vcs.log.data.AbstractDataGetter.Companion.getCommitDetails
import com.intellij.vcs.log.data.index.IndexDiagnostic.getDiffFor
import com.intellij.vcs.log.data.index.IndexDiagnostic.pickCommits
import com.intellij.vcs.log.data.index.IndexDiagnostic.pickIndexedCommits
import com.intellij.vcs.log.impl.VcsLogErrorHandler
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import org.jetbrains.annotations.ApiStatus.Internal
import kotlin.time.Duration.Companion.milliseconds


internal class IndexDiagnosticRunner(
  private val index: VcsLogModifiableIndex,
  private val storage: VcsLogStorage,
  private val roots: Collection<VirtualFile>,
  private val dataPackGetter: () -> DataPack,
  private val commitDetailsGetter: CommitDetailsGetter,
  private val errorHandler: VcsLogErrorHandler,
  vcsLogData: VcsLogData
) : Disposable {

  @Suppress("SSBasedInspection")
  private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO).also {
    Disposer.register(this) {
      it.cancel()
    }
  }

  private val bigRepositoriesList = VcsLogBigRepositoriesList.getInstance()
  private val rootsFlow = callbackFlow {
    val indexListener = VcsLogIndex.IndexingFinishedListener { root ->
      trySend(listOf(root))
    }
    val bigRepoListener = object : VcsLogBigRepositoriesList.Listener {
      override fun onRepositoryAdded(root: VirtualFile) {
        trySend(listOf(root))
      }
    }
    val dataPackListener = DataPackChangeListener {
      trySend(roots.filter { root -> index.isIndexed(root) || bigRepositoriesList.isBig(root) })
    }

    bigRepositoriesList.addListener(bigRepoListener, this@IndexDiagnosticRunner)
    index.addListener(indexListener)

    awaitClose {
      index.removeListener(indexListener)
      vcsLogData.removeDataPackChangeListener(dataPackListener)
    }
  }

  private val checkedRoots = ConcurrentCollectionFactory.createConcurrentSet<VirtualFile>()

  init {
    Disposer.register(vcsLogData, this)
    coroutineScope.launch {
      rootsFlow.collect(::runDiagnostic)
    }
  }

  private suspend fun runDiagnostic(rootsToCheck: Collection<VirtualFile>) {
    try {
      withTimeout(DIAGNOSTIC_TIMEOUT) {
        doRunDiagnostic(rootsToCheck)
      }
    }
    catch (e: TimeoutCancellationException) {
      thisLogger<IndexDiagnosticRunner>().warn("Index diagnostic for $rootsToCheck is cancelled by timeout")
      throw e
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


  @Suppress("SSBasedInspection")
  override fun dispose() {
    runBlocking {
      try {
        withTimeout(10.milliseconds) {
          coroutineScope.coroutineContext.job.join()
        }
      }
      catch (e: TimeoutCancellationException) {
        thisLogger().warn("Index diagnostic shutdown for $roots is cancelled by timeout")
      }
    }
  }

  companion object {
    private const val DIAGNOSTIC_TIMEOUT: Long = 3 * 60 * 1000
  }
}
