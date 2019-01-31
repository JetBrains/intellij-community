// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.ui.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.vcs.FilePath
import com.intellij.vcs.log.VcsLogDataKeys
import com.intellij.vcs.log.statistics.VcsLogUsageTriggerCollector
import com.intellij.vcs.log.ui.VcsLogInternalDataKeys
import com.intellij.vcsUtil.VcsUtil

open class CompareRevisionsFromLogAction : DumbAwareAction() {
  protected open fun getFilePath(e: AnActionEvent): FilePath? {
    val log = e.getData(VcsLogDataKeys.VCS_LOG) ?: return null
    val selectedCommits = log.selectedCommits
    if (selectedCommits.isEmpty() || selectedCommits.size > 2) return null
    if (selectedCommits.first().root != selectedCommits.last().root) return null
    return VcsUtil.getFilePath(selectedCommits.first().root)
  }

  override fun update(e: AnActionEvent) {
    val log = e.getData(VcsLogDataKeys.VCS_LOG)
    val handler = e.getData(VcsLogInternalDataKeys.LOG_DIFF_HANDLER)
    val filePath = getFilePath(e)
    if (log == null || filePath == null || handler == null) {
      e.presentation.isEnabledAndVisible = false
      return
    }

    e.presentation.isVisible = true
    e.presentation.isEnabled = log.selectedCommits.size == 2
  }

  override fun actionPerformed(e: AnActionEvent) {
    val log = e.getRequiredData(VcsLogDataKeys.VCS_LOG)
    val handler = e.getRequiredData(VcsLogInternalDataKeys.LOG_DIFF_HANDLER)
    val filePath = getFilePath(e)!!

    VcsLogUsageTriggerCollector.triggerUsage(e)

    val commits = log.selectedCommits
    if (commits.size == 2) {
      handler.showDiff(commits[1].root, filePath, commits[1].hash, filePath, commits[0].hash)
    }
  }

}