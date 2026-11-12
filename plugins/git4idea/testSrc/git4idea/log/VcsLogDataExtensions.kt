// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.log

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.vcs.log.data.DataPackChangeListener
import com.intellij.vcs.log.data.LoggingErrorHandler
import com.intellij.vcs.log.data.VcsLogData
import com.intellij.vcs.log.data.index.VcsLogIndex
import com.intellij.vcs.log.impl.VcsLogSharedSettings
import git4idea.repo.GitRepository
import junit.framework.TestCase.fail
import kotlinx.coroutines.CoroutineScope
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

private val LOG = Logger.getInstance("Git.Test.LogData.Extensions")

internal fun createLogDataIn(cs: CoroutineScope, repo: GitRepository, logProvider: GitLogProvider): VcsLogData {
  return VcsLogData(repo.project,
                    cs,
                    mapOf(repo.root to logProvider),
                    LoggingErrorHandler(LOG),
                    VcsLogSharedSettings.isIndexSwitchedOn(repo.project))
}

internal fun VcsLogData.refreshAndWait(repo: GitRepository, waitIndexFinishing: Boolean) {
  val logWaiter = CompletableFuture<VcsLogData>()
  val dataPackChangeListener = DataPackChangeListener { newDataPack ->
    if (newDataPack.isFull) {
      logWaiter.complete(this)
    }
  }
  addDataPackChangeListener(dataPackChangeListener)
  refresh(listOf(repo.root))
  try {
    logWaiter.get(5, TimeUnit.SECONDS)
    if (waitIndexFinishing) {
      waitIndexFinishing(repo)
    }
  }
  catch (e: Exception) {
    fail(e.message)
  }
  finally {
    removeDataPackChangeListener(dataPackChangeListener)
  }
}

private fun VcsLogData.waitIndexFinishing(repo: GitRepository) {
  val repositoryRoot = repo.root
  if (!index.indexingRoots.contains(repositoryRoot)) return

  val indexWaiter = CompletableFuture<VirtualFile>()
  val indexFinishedListener = VcsLogIndex.IndexingFinishedListener { root ->
    if (repositoryRoot == root) {
      indexWaiter.complete(root)
    }
  }
  index.addListener(indexFinishedListener)
  try {
    if (index.isIndexed(repositoryRoot)) {
      indexWaiter.complete(repositoryRoot)
    }
    indexWaiter.get(5, TimeUnit.SECONDS)
  }
  catch (e: Exception) {
    fail(e.message)
  }
  finally {
    index.removeListener(indexFinishedListener)
  }
}