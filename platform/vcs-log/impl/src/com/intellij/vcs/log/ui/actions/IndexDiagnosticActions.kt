// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.ui.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.MessageType
import com.intellij.openapi.util.NlsActions
import com.intellij.openapi.vcs.ui.VcsBalloonProblemNotifier
import com.intellij.testFramework.LightVirtualFile
import com.intellij.util.Consumer
import com.intellij.util.text.DateFormatUtil
import com.intellij.vcs.log.VcsFullCommitDetails
import com.intellij.vcs.log.VcsLog
import com.intellij.vcs.log.VcsLogBundle
import com.intellij.vcs.log.VcsLogDataKeys
import com.intellij.vcs.log.data.VcsLogData
import com.intellij.vcs.log.data.index.IndexDiagnostic.getDiffFor
import com.intellij.vcs.log.data.index.IndexDataGetter
import com.intellij.vcs.log.data.index.VcsLogPersistentIndex
import com.intellij.vcs.log.graph.api.LiteLinearGraph
import com.intellij.vcs.log.graph.api.permanent.PermanentGraphInfo
import com.intellij.vcs.log.graph.utils.BfsWalk
import com.intellij.vcs.log.graph.utils.IntHashSetFlags
import com.intellij.vcs.log.graph.utils.LinearGraphUtils
import com.intellij.vcs.log.impl.VcsLogManager
import com.intellij.vcs.log.impl.VcsProjectLog
import it.unimi.dsi.fastutil.ints.IntArrayList
import java.util.*
import java.util.function.Supplier

abstract class IndexDiagnosticActionBase(dynamicText: Supplier<@NlsActions.ActionText String>) : DumbAwareAction(dynamicText) {
  override fun update(e: AnActionEvent) {
    val project = e.project
    if (project == null) {
      e.presentation.isEnabledAndVisible = false
      return
    }
    val logManager = VcsProjectLog.getInstance(project).logManager
    if (logManager == null) {
      e.presentation.isEnabledAndVisible = false
      return
    }
    if (logManager.dataManager.index.dataGetter == null) {
      e.presentation.isEnabledAndVisible = false
      return
    }
    update(e, logManager)
  }

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project!!
    val logManager = VcsProjectLog.getInstance(project).logManager ?: return
    val dataGetter = logManager.dataManager.index.dataGetter ?: return

    val commitIds = getCommitsToCheck(e, logManager)
    if (commitIds.isEmpty()) return

    logManager.dataManager.commitDetailsGetter.loadCommitsData(commitIds, Consumer { commitDetails ->
      runCheck(project, dataGetter, commitIds, commitDetails)
    }, thisLogger()::error, null)
  }

  private fun runCheck(project: Project,
                       dataGetter: IndexDataGetter,
                       commitsListToCheck: List<Int>,
                       details: List<VcsFullCommitDetails>) {
    val report = StringBuilder()
    ProgressManager.getInstance().runProcessWithProgressSynchronously(Runnable {
      for ((commitId, commitDetails) in commitsListToCheck.zip(details)) {
        dataGetter.getDiffFor(commitId, commitDetails)?.let { commitReport ->
          report.append(commitReport).append("\n")
        }
      }
    }, VcsLogBundle.message("vcs.log.index.diagnostic.progress.title"), false, project)
    if (report.isBlank()) {
      VcsBalloonProblemNotifier.showOverVersionControlView(project, VcsLogBundle.message("vcs.log.index.diagnostic.success.message",
                                                                                         commitsListToCheck.size),
                                                           MessageType.INFO)
      return
    }

    val reportFile = LightVirtualFile(VcsLogBundle.message("vcs.log.index.diagnostic.report.title", DateFormatUtil.formatDateTime(Date())),
                                      report.toString())
    OpenFileDescriptor(project, reportFile, 0).navigate(true)
  }

  abstract fun update(e: AnActionEvent, logManager: VcsLogManager)
  abstract fun getCommitsToCheck(e: AnActionEvent, logManager: VcsLogManager): List<Int>
}

class CheckSelectedCommits :
  IndexDiagnosticActionBase(VcsLogBundle.messagePointer("vcs.log.index.diagnostic.selected.action.title")) {

  override fun update(e: AnActionEvent, logManager: VcsLogManager) {
    val vcsLog = e.getData(VcsLogDataKeys.VCS_LOG)
    if (vcsLog == null) {
      e.presentation.isEnabledAndVisible = false
      return
    }
    val selectedCommits = getSelectedCommits(logManager.dataManager, vcsLog, false)
    e.presentation.isVisible = true
    e.presentation.isEnabled = selectedCommits.isNotEmpty()
  }

  private fun getSelectedCommits(vcsLogData: VcsLogData, vcsLog: VcsLog, reportNotIndexed: Boolean): List<Int> {
    val selectedCommits = vcsLog.selectedCommits.map { vcsLogData.getCommitIndex(it.hash, it.root) }
    if (selectedCommits.isEmpty()) return emptyList()

    val selectedIndexedCommits = selectedCommits.filter { vcsLogData.index.isIndexed(it) }
    if (selectedIndexedCommits.isEmpty() && reportNotIndexed) {
      VcsBalloonProblemNotifier.showOverVersionControlView(vcsLogData.project,
                                                           VcsLogBundle.message("vcs.log.index.diagnostic.selected.non.indexed.warning.message"),
                                                           MessageType.WARNING)
    }
    return selectedIndexedCommits
  }

  override fun getCommitsToCheck(e: AnActionEvent, logManager: VcsLogManager): List<Int> {
    return getSelectedCommits(logManager.dataManager, e.getRequiredData(VcsLogDataKeys.VCS_LOG), true)
  }
}

class CheckOldCommits : IndexDiagnosticActionBase(VcsLogBundle.messagePointer("vcs.log.index.diagnostic.action.title")) {

  override fun update(e: AnActionEvent, logManager: VcsLogManager) {
    val rootsForIndexing = VcsLogPersistentIndex.getRootsForIndexing(logManager.dataManager.logProviders)
    if (rootsForIndexing.isEmpty()) {
      e.presentation.isEnabledAndVisible = false
      return
    }
    e.presentation.isVisible = true
    e.presentation.isEnabled = logManager.dataManager.dataPack.isFull &&
                               rootsForIndexing.any { logManager.dataManager.index.isIndexed(it) }
  }

  override fun getCommitsToCheck(e: AnActionEvent, logManager: VcsLogManager): List<Int> {
    val indexedRoots = VcsLogPersistentIndex.getRootsForIndexing(logManager.dataManager.logProviders).filter {
      logManager.dataManager.index.isIndexed(it)
    }
    if (indexedRoots.isEmpty()) return emptyList()
    val dataPack = logManager.dataManager.dataPack
    if (!dataPack.isFull) return emptyList()

    val rootsToCheck = indexedRoots.toMutableSet()
    val commitsToCheck = IntArrayList()

    @Suppress("UNCHECKED_CAST") val permanentGraphInfo = dataPack.permanentGraph as? PermanentGraphInfo<Int> ?: return emptyList()
    val graph = LinearGraphUtils.asLiteLinearGraph(permanentGraphInfo.linearGraph)
    for (node in graph.nodesCount() - 1 downTo 0) {
      if (!graph.getNodes(node, LiteLinearGraph.NodeFilter.DOWN).isEmpty()) continue

      val root = logManager.dataManager.getCommitId(permanentGraphInfo.permanentCommitsInfo.getCommitId(node))?.root
      if (!rootsToCheck.remove(root)) continue

      // initial commit may not have files (in case of shallow clone), or it may have too many files
      // checking next commits instead
      BfsWalk(node, graph, IntHashSetFlags(COMMITS_TO_CHECK), false).walk { nextNode ->
        if (nextNode != node && graph.getNodes(nextNode, LiteLinearGraph.NodeFilter.DOWN).size == 1) {
          // skipping merge commits since they can have too many changes
          commitsToCheck.add(permanentGraphInfo.permanentCommitsInfo.getCommitId(nextNode))
        }
        return@walk commitsToCheck.size < COMMITS_TO_CHECK
      }
      if (rootsToCheck.isEmpty()) break
    }

    return commitsToCheck
  }

  companion object {
    const val COMMITS_TO_CHECK = 10
  }
}