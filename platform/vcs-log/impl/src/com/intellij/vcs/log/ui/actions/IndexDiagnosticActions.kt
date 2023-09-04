// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.ui.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.util.NlsActions
import com.intellij.openapi.util.ThrowableComputable
import com.intellij.openapi.vcs.VcsNotifier
import com.intellij.testFramework.LightVirtualFile
import com.intellij.util.text.DateFormatUtil
import com.intellij.vcs.log.VcsLogBundle
import com.intellij.vcs.log.VcsLogCommitSelection
import com.intellij.vcs.log.VcsLogDataKeys
import com.intellij.vcs.log.data.AbstractDataGetter.Companion.getCommitDetails
import com.intellij.vcs.log.data.VcsLogData
import com.intellij.vcs.log.data.index.IndexDiagnostic.getDiffFor
import com.intellij.vcs.log.data.index.IndexDiagnostic.pickCommits
import com.intellij.vcs.log.impl.VcsLogManager
import com.intellij.vcs.log.impl.VcsProjectLog
import java.util.*
import java.util.function.Supplier

abstract class IndexDiagnosticActionBase(dynamicText: Supplier<@NlsActions.ActionText String>) : DumbAwareAction(dynamicText) {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

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

    val report = ProgressManager.getInstance().runProcessWithProgressSynchronously(ThrowableComputable {
      val detailsList = logManager.dataManager.commitDetailsGetter.getCommitDetails(commitIds)
      return@ThrowableComputable dataGetter.getDiffFor(commitIds, detailsList)
    }, VcsLogBundle.message("vcs.log.index.diagnostic.progress.title"), true, project)
    if (report.isBlank()) {
      VcsNotifier.getInstance(project).notifyInfo(null, "", VcsLogBundle.message("vcs.log.index.diagnostic.success.message",
                                                                                 commitIds.size))
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
    val selection = e.getData(VcsLogDataKeys.VCS_LOG_COMMIT_SELECTION)
    if (selection == null) {
      e.presentation.isEnabledAndVisible = false
      return
    }
    val selectedCommits = getSelectedCommits(logManager.dataManager, selection, false)
    e.presentation.isVisible = true
    e.presentation.isEnabled = selectedCommits.isNotEmpty()
  }

  private fun getSelectedCommits(vcsLogData: VcsLogData, selection: VcsLogCommitSelection, reportNotIndexed: Boolean): List<Int> {
    val selectedCommits = selection.ids
    if (selectedCommits.isEmpty()) return emptyList()

    val selectedIndexedCommits = selectedCommits.filter { vcsLogData.index.isIndexed(it) }
    if (selectedIndexedCommits.isEmpty() && reportNotIndexed) {
      VcsNotifier.getInstance(vcsLogData.project).notifyWarning(null, "",
                                                                VcsLogBundle.message("vcs.log.index.diagnostic.selected.non.indexed.warning.message"))
    }
    return selectedIndexedCommits
  }

  override fun getCommitsToCheck(e: AnActionEvent, logManager: VcsLogManager): List<Int> {
    return getSelectedCommits(logManager.dataManager, e.getRequiredData(VcsLogDataKeys.VCS_LOG_COMMIT_SELECTION), true)
  }
}

class CheckOldCommits : IndexDiagnosticActionBase(VcsLogBundle.messagePointer("vcs.log.index.diagnostic.action.title")) {

  override fun update(e: AnActionEvent, logManager: VcsLogManager) {
    val rootsForIndexing = logManager.dataManager.index.indexingRoots
    if (rootsForIndexing.isEmpty()) {
      e.presentation.isEnabledAndVisible = false
      return
    }
    e.presentation.isVisible = true
    e.presentation.isEnabled = logManager.dataManager.dataPack.isFull &&
                               rootsForIndexing.any { logManager.dataManager.index.isIndexed(it) }
  }

  override fun getCommitsToCheck(e: AnActionEvent, logManager: VcsLogManager): List<Int> {
    val indexedRoots = logManager.dataManager.index.indexingRoots.filter {
      logManager.dataManager.index.isIndexed(it)
    }
    if (indexedRoots.isEmpty()) return emptyList()
    val dataPack = logManager.dataManager.dataPack
    if (!dataPack.isFull) return emptyList()

    return dataPack.pickCommits(logManager.dataManager.storage, indexedRoots, true).toList()
  }
}