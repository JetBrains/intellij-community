// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.log

import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.vcs.log.data.DataPackChangeListener
import com.intellij.vcs.log.data.LoggingErrorHandler
import com.intellij.vcs.log.data.VcsLogData
import com.intellij.vcs.log.data.index.VcsLogIndex
import git4idea.repo.GitRepository
import junit.framework.TestCase.fail
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

private val LOG = Logger.getInstance("Git.Test.LogData.Extensions")

internal fun createLogData(repo: GitRepository, logProvider: GitLogProvider, disposable: Disposable): VcsLogData {
  return VcsLogData(repo.project, mapOf(repo.root to logProvider), LoggingErrorHandler(LOG), disposable)
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
  val indexWaiter = CompletableFuture<VirtualFile>()
  val repositoryRoot = repo.root
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