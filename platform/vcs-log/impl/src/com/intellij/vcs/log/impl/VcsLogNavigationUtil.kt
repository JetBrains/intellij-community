// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.impl

import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.google.common.util.concurrent.SettableFuture
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.indicatorRunBlockingCancellable
import com.intellij.openapi.progress.runBackgroundableTask
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.MessageType
import com.intellij.openapi.util.IntRef
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentManager
import com.intellij.openapi.vcs.ui.VcsBalloonProblemNotifier
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.vcs.log.CommitId
import com.intellij.vcs.log.Hash
import com.intellij.vcs.log.VcsLogBundle
import com.intellij.vcs.log.VcsLogFilterCollection
import com.intellij.vcs.log.data.*
import com.intellij.vcs.log.data.DataPack.ErrorDataPack
import com.intellij.vcs.log.graph.api.permanent.PermanentGraphInfo
import com.intellij.vcs.log.graph.impl.facade.VisibleGraphImpl
import com.intellij.vcs.log.ui.MainVcsLogUi
import com.intellij.vcs.log.ui.VcsLogUiEx
import com.intellij.vcs.log.ui.VcsLogUiEx.JumpResult
import com.intellij.vcs.log.util.VcsLogUtil
import com.intellij.vcs.log.visible.VisiblePack
import com.intellij.vcs.log.visible.VisiblePack.ErrorVisiblePack
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
      indicatorRunBlockingCancellable(indicator) {
        resultFuture.computeResult {
          withContext(Dispatchers.EDT) {
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

    val otherUis = manager.getLogUis(VcsLogTabLocation.TOOL_WINDOW).filterIsInstance<MainVcsLogUi>() - selectedUis.toSet()
    otherUis.find { ui ->
      ui.refresher.setValid(true, false)
      predicate(ui) && ui.showCommit(hash, root, requestFocus)
    }?.let { ui ->
      VcsLogContentUtil.selectLogUi(project, ui, requestFocus)
      return ui
    }

    val newUi = VcsProjectLog.getInstance(project).openLogTab(VcsLogFilterObject.EMPTY_COLLECTION,
                                                              VcsLogTabLocation.TOOL_WINDOW) ?: return null
    if (newUi.showCommit(hash, root, requestFocus)) return newUi
    return null
  }

  private suspend fun MainVcsLogUi.showCommit(hash: Hash, root: VirtualFile,
                                              requestFocus: Boolean): Boolean {
    val jumpResult = jumpToCommitInternal(hash, root, true, requestFocus).await()
    return when (jumpResult) {
      JumpResult.SUCCESS -> true
      null, JumpResult.COMMIT_NOT_FOUND -> {
        LOG.warn("Commit $hash for $root not found in $this")
        false
      }
      JumpResult.COMMIT_DOES_NOT_MATCH -> false
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
    suspendCancellableCoroutine { continuation ->
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

  /**
   * Asynchronously selects the commit node at the given [row].
   * @param row      target row
   * @param silently skip showing notification when the target is not found
   * @param focus    focus the table
   */
  @JvmStatic
  fun VcsLogUiEx.jumpToRow(row: Int, silently: Boolean, focus: Boolean) {
    jumpTo(row, { visiblePack, r ->
      if (visiblePack.visibleGraph.visibleCommitCount <= r) return@jumpTo -1
      r
    }, SettableFuture.create(), silently, focus)
  }

  /**
   * Asynchronously selects the commit node at the given branch head.
   * @param branchName name of the target branch
   * @param silently   skip showing notification when the target is not found
   * @param focus      focus the table
   */
  @JvmStatic
  fun VcsLogUiEx.jumpToBranch(branchName: String, silently: Boolean, focus: Boolean) {
    jumpTo(branchName, { visiblePack, branch ->
      return@jumpTo getBranchRow(logData, visiblePack, branch)
    }, SettableFuture.create(), silently, focus)
  }

  /**
   * Asynchronously selects the commit node defined by the given reference (commit hash, branch or tag).
   *
   * Note: this function decides if the provided reference is a hash or a branch/tag once at the start.
   * This may not work as expected when log is not up-to-date, since all the branches and tags are not available yet.
   *
   * @param reference target reference (commit hash, branch or tag)
   * @param silently  skip showing notification when the target is not found
   * @param focus     focus the table
   * @return future result (success or failure)
   */
  @JvmStatic
  fun VcsLogUiEx.jumpToRefOrHash(reference: String, silently: Boolean, focus: Boolean): ListenableFuture<Boolean> {
    if (reference.isBlank()) return Futures.immediateFuture(false)
    val future = SettableFuture.create<Boolean>()
    val refs = dataPack.refs
    ApplicationManager.getApplication().executeOnPooledThread {
      val matchingRefs = refs.stream().filter { ref -> ref.name.startsWith(reference) }.toList()
      ApplicationManager.getApplication().invokeLater {
        if (matchingRefs.isNotEmpty()) {
          val ref = matchingRefs.minWith(VcsGoToRefComparator(dataPack.logProviders))
          future.setFuture(jumpToCommit(ref.commitHash, ref.root, silently, focus))
          return@invokeLater
        }

        future.setFuture(jumpToHash(reference, silently, focus))
      }
    }
    return future
  }

  /**
   * Asynchronously selects the commit node defined by the given hash.
   *
   * @param commitHash target commit hash
   * @param silently   skip showing notification when the target is not found
   * @param focus      focus the table
   * @return future result (success or failure)
   */
  @JvmStatic
  fun VcsLogUiEx.jumpToHash(commitHash: String, silently: Boolean, focus: Boolean): ListenableFuture<Boolean> {
    val trimmedHash = StringUtil.trim(commitHash) { ch -> !StringUtil.containsChar("()'\"`", ch) }

    if (!VcsLogUtil.HASH_PREFIX_REGEX.matcher(trimmedHash).matches()) {
      if (!silently) {
        VcsBalloonProblemNotifier.showOverChangesView(logData.project,
                                                      VcsLogBundle.message("vcs.log.string.is.not.a.hash", commitHash),
                                                      MessageType.WARNING)
      }
      return Futures.immediateFuture(false)
    }

    val future = SettableFuture.create<JumpResult>()
    jumpTo(trimmedHash, { visiblePack, partialHash -> getCommitRow(logData, visiblePack, partialHash) }, future, silently, focus)
    return mapToJumpSuccess(future)
  }

  /**
   * Asynchronously selects the given commit node.
   *
   * @param commitHash target commit hash
   * @param root       target commit repository root
   * @param silently   skip showing notification when the target is not found
   * @param focus      focus the table
   * @return future result (success or failure)
   */
  @JvmStatic
  fun VcsLogUiEx.jumpToCommit(commitHash: Hash, root: VirtualFile, silently: Boolean, focus: Boolean): ListenableFuture<Boolean> {
    return mapToJumpSuccess(jumpToCommitInternal(commitHash, root, silently, focus))
  }

  @JvmStatic
  private fun VcsLogUiEx.jumpToCommitInternal(commitHash: Hash,
                                              root: VirtualFile,
                                              silently: Boolean,
                                              focus: Boolean): ListenableFuture<JumpResult> {
    val future = SettableFuture.create<JumpResult>()
    jumpTo(commitHash, { visiblePack, hash ->
      if (!logData.storage.containsCommit(CommitId(hash, root))) return@jumpTo VcsLogUiEx.COMMIT_NOT_FOUND
      getCommitRow(logData.storage, visiblePack, hash, root)
    }, future, silently, focus)
    return future
  }

  private fun getBranchRow(vcsLogData: VcsLogData, visiblePack: VisiblePack, referenceName: String): Int {
    val matchingRefs = visiblePack.refs.branches.filter { ref -> ref.name == referenceName }
    if (matchingRefs.isEmpty()) {
      return VcsLogUiEx.COMMIT_NOT_FOUND
    }
    val ref = matchingRefs.minWith(VcsGoToRefComparator(visiblePack.logProviders))
    return getCommitRow(vcsLogData.storage, visiblePack, ref.commitHash, ref.root)
  }

  private fun getCommitRow(vcsLogData: VcsLogData, visiblePack: VisiblePack, partialHash: String): Int {
    if (partialHash.length == VcsLogUtil.FULL_HASH_LENGTH) {
      var row = VcsLogUiEx.COMMIT_NOT_FOUND
      val candidateHash = HashImpl.build(partialHash)
      for (candidateRoot in vcsLogData.roots) {
        if (vcsLogData.storage.containsCommit(CommitId(candidateHash, candidateRoot))) {
          val candidateRow = getCommitRow(vcsLogData.storage, visiblePack, candidateHash, candidateRoot)
          if (candidateRow >= 0) return candidateRow
          if (row == VcsLogUiEx.COMMIT_NOT_FOUND) row = candidateRow
        }
      }
      return row
    }
    val row = IntRef(VcsLogUiEx.COMMIT_NOT_FOUND)
    vcsLogData.storage.iterateCommits { candidate ->
      if (CommitIdByStringCondition.matches(candidate, partialHash)) {
        val candidateRow = getCommitRow(vcsLogData.storage, visiblePack, candidate.hash, candidate.root)
        if (row.get() == VcsLogUiEx.COMMIT_NOT_FOUND) row.set(candidateRow)
        return@iterateCommits candidateRow < 0
      }
      true
    }
    return row.get()
  }

  private fun getCommitRow(storage: VcsLogStorage, visiblePack: VisiblePack, hash: Hash, root: VirtualFile): Int {
    if (visiblePack.dataPack is ErrorDataPack) return VcsLogUiEx.COMMIT_NOT_FOUND
    if (visiblePack is ErrorVisiblePack) return VcsLogUiEx.COMMIT_DOES_NOT_MATCH

    val commitIndex = storage.getCommitIndex(hash, root)
    val visibleGraph = visiblePack.visibleGraph
    if (visibleGraph is VisibleGraphImpl<*>) {
      val nodeId = (visibleGraph as VisibleGraphImpl<Int>).permanentGraph.permanentCommitsInfo.getNodeId(commitIndex)
      if (nodeId == VcsLogUiEx.COMMIT_NOT_FOUND) return VcsLogUiEx.COMMIT_NOT_FOUND
      if (nodeId < 0) return VcsLogUiEx.COMMIT_DOES_NOT_MATCH
      return visibleGraph.linearGraph.getNodeIndex(nodeId) ?: VcsLogUiEx.COMMIT_DOES_NOT_MATCH
    }
    return visibleGraph.getVisibleRowIndex(commitIndex) ?: VcsLogUiEx.COMMIT_DOES_NOT_MATCH
  }

  private fun mapToJumpSuccess(future: ListenableFuture<JumpResult>): ListenableFuture<Boolean> {
    return Futures.transform(future, { it == JumpResult.SUCCESS }, MoreExecutors.directExecutor())
  }
}