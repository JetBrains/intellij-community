// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.ui.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.vcs.log.VcsLogDataKeys
import com.intellij.vcs.log.statistics.VcsLogUsageTriggerCollector
import com.intellij.vcs.log.ui.VcsLogInternalDataKeys
import com.intellij.vcs.log.util.VcsLogUtil

class CompareRevisionsFromLogAction : DumbAwareAction() {
  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }

  override fun update(e: AnActionEvent) {
    val selection = e.getData(VcsLogDataKeys.VCS_LOG_COMMIT_SELECTION)
    val handler = e.getData(VcsLogInternalDataKeys.LOG_DIFF_HANDLER)
    if (selection == null || handler == null) {
      e.presentation.isEnabledAndVisible = false
      return
    }

    val commits = selection.commits

    e.presentation.isVisible = commits.size == 2
    e.presentation.isEnabled = commits.size == 2 && commits.first().root == commits.last().root
  }

  override fun actionPerformed(e: AnActionEvent) {
    val selection = e.getRequiredData(VcsLogDataKeys.VCS_LOG_COMMIT_SELECTION)
    val handler = e.getRequiredData(VcsLogInternalDataKeys.LOG_DIFF_HANDLER)

    VcsLogUsageTriggerCollector.triggerUsage(e, this)

    val commits = selection.commits
    if (commits.size == 2) {
      val root = commits.first().root
      handler.showDiffForPaths(root, VcsLogUtil.getAffectedPaths(root, e), commits[1].hash, commits[0].hash)
    }
  }

}