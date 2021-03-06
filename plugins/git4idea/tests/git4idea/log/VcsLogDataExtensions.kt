// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.log

import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.vcs.log.data.DataPackChangeListener
import com.intellij.vcs.log.data.VcsLogData
import com.intellij.vcs.log.data.index.VcsLogIndex
import com.intellij.vcs.log.impl.FatalErrorHandler
import git4idea.repo.GitRepository
import junit.framework.TestCase.fail
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

private val LOG = Logger.getInstance("Git.Test.LogData.Extensions")

internal fun createLogData(repo: GitRepository, logProvider: GitLogProvider, disposable: Disposable): VcsLogData {
  return VcsLogData(repo.project, mapOf(repo.root to logProvider), object : FatalErrorHandler {
    override fun consume(source: Any?, throwable: Throwable) {
      LOG.error(throwable)
    }

    override fun displayFatalErrorMessage(message: String) {
      LOG.error(message)
    }
  }, disposable)
}

internal fun VcsLogData.refreshAndWait(repo: GitRepository, withIndex: Boolean = false) {
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
    if (withIndex) {
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
  val indexFinishedListener = VcsLogIndex.IndexingFinishedListener { root ->
    if (repo.root == root) {
      indexWaiter.complete(root)
    }
  }
  index.addListener(indexFinishedListener)
  try {
    indexWaiter.get(5, TimeUnit.SECONDS)
  }
  catch (e: Exception) {
    fail(e.message)
  }
  finally {
    index.removeListener(indexFinishedListener)
  }
}