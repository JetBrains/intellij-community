/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.vcs.log.ui.actions.history

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.AnActionExtensionProvider
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.ui.MessageType
import com.intellij.openapi.vcs.VcsDataKeys
import com.intellij.openapi.vcs.history.VcsDiffUtil
import com.intellij.openapi.vcs.ui.VcsBalloonProblemNotifier
import com.intellij.util.ObjectUtils.notNull
import com.intellij.util.containers.ContainerUtil
import com.intellij.vcs.log.VcsLog
import com.intellij.vcs.log.ui.VcsLogInternalDataKeys
import com.intellij.vcs.log.statistics.VcsLogUsageTriggerCollector
import java.awt.event.KeyEvent

class CompareRevisionsFromHistoryActionProvider : AnActionExtensionProvider {

  override fun isActive(e: AnActionEvent): Boolean {
    val filePath = e.getData(VcsDataKeys.FILE_PATH)
    return e.getData(VcsLogInternalDataKeys.FILE_HISTORY_UI) != null && filePath != null && filePath.isDirectory
  }

  override fun update(e: AnActionEvent) {
    val project = e.project
    val ui = e.getData(VcsLogInternalDataKeys.FILE_HISTORY_UI)
    val filePath = e.getData(VcsDataKeys.FILE_PATH)
    if (project == null || ui == null || filePath == null) {
      e.presentation.isEnabledAndVisible = false
      return
    }
    e.presentation.isVisible = true

    val log = ui.vcsLog
    updateActionText(e, log)

    if (e.inputEvent is KeyEvent) {
      e.presentation.isEnabled = true
      return
    }

    val commits = log.selectedCommits
    if (commits.size == 2) {
      e.presentation.isEnabled = e.getData(VcsLogInternalDataKeys.LOG_DIFF_HANDLER) != null
    }
    else {
      e.presentation.isEnabled = commits.size == 1
    }
  }

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.getRequiredData(CommonDataKeys.PROJECT)
    val ui = e.getRequiredData(VcsLogInternalDataKeys.FILE_HISTORY_UI)
    val filePath = e.getRequiredData(VcsDataKeys.FILE_PATH)

    VcsLogUsageTriggerCollector.triggerUsage(e)

    val commits = ui.vcsLog.selectedCommits
    if (commits.size == 2) {
      val handler = e.getData(VcsLogInternalDataKeys.LOG_DIFF_HANDLER) ?: return
      // this check is needed here since we may come on key event without performing proper checks

      val newestId = commits[0]
      val olderId = commits[1]
      notNull(handler).showDiff(olderId.root, ui.getPathInCommit(olderId.hash), olderId.hash,
                                ui.getPathInCommit(newestId.hash), newestId.hash)
      return
    }

    if (commits.size != 1) return

    val commitIds = ContainerUtil.map(commits) { c -> ui.logData.getCommitIndex(c.hash, c.root) }
    ui.logData.commitDetailsGetter.loadCommitsData(commitIds, { details ->
      val detail = notNull(ContainerUtil.getFirstItem(details))
      val changes = ui.collectRelevantChanges(detail)
      VcsDiffUtil.showChangesDialog(project, "Changes in " + detail.id.toShortString() + " for " + filePath.name,
                                    ContainerUtil.newArrayList(changes))
    }, { t -> VcsBalloonProblemNotifier.showOverChangesView(project, "Could not load selected commits: " + t.message, MessageType.ERROR) },
                                                   null)
  }

  companion object {
    private const val COMPARE_TEXT = "Compare"
    private const val COMPARE_DESCRIPTION = "Compare selected versions"
    private const val DIFF_TEXT = "Show Diff"
    private const val DIFF_DESCRIPTION = "Show diff with previous version"

    @JvmStatic
    fun updateActionText(e: AnActionEvent, log: VcsLog) {
      if (log.selectedCommits.size >= 2) {
        e.presentation.text = COMPARE_TEXT
        e.presentation.description = COMPARE_DESCRIPTION
      }
      else {
        e.presentation.text = DIFF_TEXT
        e.presentation.description = DIFF_DESCRIPTION
      }
    }
  }
}
