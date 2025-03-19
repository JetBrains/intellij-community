// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.ui.actions.history

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.VcsNotifier
import com.intellij.vcs.log.*
import com.intellij.vcs.log.data.LoadingDetails
import com.intellij.vcs.log.data.VcsLogData
import com.intellij.vcs.log.history.FileHistoryModel
import com.intellij.vcs.log.statistics.VcsLogUsageTriggerCollector
import com.intellij.vcs.log.ui.VcsLogInternalDataKeys
import com.intellij.vcs.log.ui.VcsLogNotificationIdsHolder
import com.intellij.vcs.log.ui.table.lazyMap
import com.intellij.vcs.log.ui.table.size
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
abstract class FileHistoryOneCommitAction<T : VcsCommitMetadata> : AnAction(), DumbAware {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    val project = e.project
    val logData = e.getData(VcsLogInternalDataKeys.LOG_DATA)
    val selection = e.getData(VcsLogDataKeys.VCS_LOG_COMMIT_SELECTION)
    val model = e.getData(VcsLogInternalDataKeys.FILE_HISTORY_MODEL)
    if (project == null || logData == null || selection == null || model == null) {
      e.presentation.isEnabledAndVisible = false
      return
    }
    e.presentation.isVisible = true
    if (selection.size != 1) {
      e.presentation.isEnabled = false
      return
    }
    val detail = selection.lazyMap(getCache(logData)::getCachedData).singleOrNull()?.takeIf { it !is LoadingDetails }
    e.presentation.isEnabled = isEnabled(model, detail, e)
  }

  override fun actionPerformed(e: AnActionEvent) {
    VcsLogUsageTriggerCollector.triggerUsage(e, this)
    val project = e.getData(CommonDataKeys.PROJECT) ?: return
    val logData = e.getData(VcsLogInternalDataKeys.LOG_DATA) ?: return
    val selection = e.getData(VcsLogDataKeys.VCS_LOG_COMMIT_SELECTION) ?: return
    val model = e.getData(VcsLogInternalDataKeys.FILE_HISTORY_MODEL) ?: return

    if (selection.size != 1) return

    loadData(logData, selection, { details: List<T> ->
      if (!details.isEmpty()) performAction(project, model, details.single(), e)
    }, { t -> showError(project, t) })
  }

  protected open fun isEnabled(selection: FileHistoryModel, detail: T?, e: AnActionEvent): Boolean = true

  protected abstract fun getCache(logData: VcsLogData): VcsLogCommitDataCache<T>
  protected abstract fun loadData(logData: VcsLogData, selection: VcsLogCommitSelection, onSuccess: (List<T>) -> Unit, onError: (Throwable) -> Unit)
  protected abstract fun performAction(project: Project, model: FileHistoryModel, detail: T, e: AnActionEvent)
}

internal fun showError(project: Project, t: Throwable) {
  VcsNotifier.getInstance(project).notifyError(VcsLogNotificationIdsHolder.FILE_HISTORY_ACTION_LOAD_DETAILS_ERROR,
                                               "", VcsLogBundle.message("file.history.action.could.not.load.selected.commits.message",
                                                                        t.message))
}