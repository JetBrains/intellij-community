// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.impl

import com.intellij.openapi.application.AppUIExecutor
import com.intellij.openapi.application.impl.coroutineDispatchingContext
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.runBackgroundableTask
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.vcs.log.CommitId
import com.intellij.vcs.log.Hash
import com.intellij.vcs.log.VcsLogBundle
import com.intellij.vcs.log.VcsLogFilterCollection
import com.intellij.vcs.log.data.DataPack
import com.intellij.vcs.log.data.DataPackChangeListener
import com.intellij.vcs.log.graph.api.permanent.PermanentGraphInfo
import com.intellij.vcs.log.ui.MainVcsLogUi
import com.intellij.vcs.log.ui.VcsLogUiEx
import com.intellij.vcs.log.util.VcsLogUtil
import com.intellij.vcs.log.visible.filters.VcsLogFilterObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.concurrent.CompletableFuture
import kotlin.coroutines.cancellation.CancellationException

object VcsLogNavigationUtil {
  private val LOG = logger<VcsLogNavigationUtil>()

  @JvmStatic
  fun jumpToRevisionAsync(project: Project, root: VirtualFile, hash: Hash, filePath: FilePath): CompletableFuture<Boolean> {
    val resultFuture = CompletableFuture<Boolean>()

    val progressTitle = VcsLogBundle.message("vcs.log.show.commit.in.log.process", hash.asString())
    runBackgroundableTask(progressTitle, project, true) { indicator ->
      runBlockingCancellable(indicator) {
        resultFuture.computeResult {
          withContext(AppUIExecutor.onUiThread().coroutineDispatchingContext()) {
            jumpToRevision(project, root, hash, filePath)
          }
        }
      }
    }

    return resultFuture
  }

  private suspend fun jumpToRevision(project: Project, root: VirtualFile, hash: Hash, filePath: FilePath): Boolean {
    val logUi = showCommitInLogTab(project, hash, root, false) { logUi ->
      if (logUi.properties.exists(MainVcsLogUiProperties.SHOW_ONLY_AFFECTED_CHANGES) &&
          logUi.properties.get(MainVcsLogUiProperties.SHOW_ONLY_AFFECTED_CHANGES) &&
          !logUi.properties.getFilterValues(VcsLogFilterCollection.STRUCTURE_FILTER.name).isNullOrEmpty()) {
        // Structure filter might prevent us from navigating to FilePath
        return@showCommitInLogTab false
      }
      return@showCommitInLogTab true
    } ?: return false

    logUi.selectFilePath(filePath, true)
    return true
  }

  /**
   * Show given commit in the changes view tool window in the log tab matching a given predicate:
   * - Try using one of the currently selected tabs if possible.
   * - Otherwise try main log tab.
   * - Otherwise create a new tab without filters and show commit there.
   */
  private suspend fun showCommitInLogTab(project: Project, hash: Hash, root: VirtualFile,
                                         requestFocus: Boolean, predicate: (MainVcsLogUi) -> Boolean): MainVcsLogUi? {
    val logInitFuture = VcsProjectLog.waitWhenLogIsReady(project)
    if (!logInitFuture.isDone) {
      withContext(Dispatchers.IO) {
        logInitFuture.get()
      }
    }
    val manager = VcsProjectLog.getInstance(project).logManager ?: return null
    val isLogUpToDate = manager.isLogUpToDate
    if (!manager.containsCommit(hash, root)) {
      if (isLogUpToDate) return null
      manager.waitForRefresh()
      if (!manager.containsCommit(hash, root)) return null
    }

    val window = ToolWindowManager.getInstance(project).getToolWindow(ChangesViewContentManager.TOOLWINDOW_ID) ?: return null
    if (!window.isVisible) {
      suspendCancellableCoroutine<Unit> { continuation ->
        window.activate { continuation.resumeWith(Result.success(Unit)) }
      }
    }

    val selectedUis = manager.getVisibleLogUis(VcsLogTabLocation.TOOL_WINDOW).filterIsInstance<MainVcsLogUi>()
    selectedUis.find { ui -> predicate(ui) && ui.showCommit(hash, root, requestFocus) }?.let { return it }

    val mainLogContent = VcsLogContentUtil.findMainLog(window.contentManager)
    if (mainLogContent != null) {
      ChangesViewContentManager.getInstanceImpl(project)?.initLazyContent(mainLogContent)

      val mainLogContentProvider = VcsLogContentProvider.getInstance(project)
      if (mainLogContentProvider != null) {
        val mainLogUi = mainLogContentProvider.waitMainUiCreation().await()
        if (!selectedUis.contains(mainLogUi)) {
          mainLogUi.refresher.setValid(true, false) // since main ui is not visible, it needs to be validated to find the commit
          if (predicate(mainLogUi) && mainLogUi.showCommit(hash, root, requestFocus)) {
            window.contentManager.setSelectedContent(mainLogContent)
            return mainLogUi
          }
        }
      }
    }

    val newUi = VcsProjectLog.getInstance(project).openLogTab(VcsLogFilterObject.EMPTY_COLLECTION,
                                                              VcsLogTabLocation.TOOL_WINDOW) ?: return null
    if (newUi.showCommit(hash, root, requestFocus)) return newUi
    return null
  }

  private suspend fun MainVcsLogUi.showCommit(hash: Hash, root: VirtualFile,
                                              requestFocus: Boolean): Boolean {
    val jumpResult = VcsLogUtil.jumpToCommit(this, hash, root, true, requestFocus).await()
    return when (jumpResult) {
      VcsLogUiEx.JumpResult.SUCCESS -> true
      null, VcsLogUiEx.JumpResult.COMMIT_NOT_FOUND -> {
        LOG.warn("Commit $hash for $root not found in $this")
        false
      }
      VcsLogUiEx.JumpResult.COMMIT_DOES_NOT_MATCH -> false
    }
  }

  private fun VcsLogManager.containsCommit(hash: Hash, root: VirtualFile): Boolean {
    if (!dataManager.storage.containsCommit(CommitId(hash, root))) return false

    val permanentGraphInfo = dataManager.dataPack.permanentGraph as? PermanentGraphInfo<Int> ?: return true

    val commitIndex = dataManager.storage.getCommitIndex(hash, root)
    val nodeId = permanentGraphInfo.permanentCommitsInfo.getNodeId(commitIndex)
    return nodeId != VcsLogUiEx.COMMIT_NOT_FOUND
  }

  private suspend fun VcsLogManager.waitForRefresh() {
    suspendCancellableCoroutine<Unit> { continuation ->
      val dataPackListener = object : DataPackChangeListener {
        override fun onDataPackChange(newDataPack: DataPack) {
          if (isLogUpToDate) {
            dataManager.removeDataPackChangeListener(this)
            continuation.resumeWith(Result.success(Unit))
          }
        }
      }
      dataManager.addDataPackChangeListener(dataPackListener)
      if (isLogUpToDate) {
        dataManager.removeDataPackChangeListener(dataPackListener)
        continuation.resumeWith(Result.success(Unit))
        return@suspendCancellableCoroutine
      }

      scheduleUpdate()

      continuation.invokeOnCancellation { dataManager.removeDataPackChangeListener(dataPackListener) }
    }
  }

  private suspend fun <T> CompletableFuture<T>.computeResult(task: suspend () -> T) {
    try {
      val result = task()
      this.complete(result)
    }
    catch (e: CancellationException) {
      this.cancel(false)
    }
    catch (e: Throwable) {
      this.completeExceptionally(e)
    }
  }
}