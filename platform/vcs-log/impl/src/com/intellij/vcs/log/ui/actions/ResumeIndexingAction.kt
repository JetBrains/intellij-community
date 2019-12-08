// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.ui.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.vcs.log.data.VcsLogData
import com.intellij.vcs.log.data.index.VcsLogBigRepositoriesList
import com.intellij.vcs.log.data.index.VcsLogModifiableIndex
import com.intellij.vcs.log.data.index.VcsLogPersistentIndex
import com.intellij.vcs.log.impl.VcsLogSharedSettings
import com.intellij.vcs.log.statistics.VcsLogUsageTriggerCollector
import com.intellij.vcs.log.ui.VcsLogInternalDataKeys

class ResumeIndexingAction : DumbAwareAction() {
  override fun update(e: AnActionEvent) {
    val data = e.getData(VcsLogInternalDataKeys.LOG_DATA)
    val project = e.project
    if (data == null || project == null || !VcsLogSharedSettings.isIndexSwitchedOn(project)) {
      e.presentation.isEnabledAndVisible = false
      return
    }

    val bigRepositories = getBigRepositories(data)
    e.presentation.isEnabledAndVisible = VcsLogPersistentIndex.getRootsForIndexing(data.logProviders).isNotEmpty() &&
                                         bigRepositories.isNotEmpty()
    e.presentation.description = "Indexing ${getText(bigRepositories)} was paused. Resume."
  }

  private fun getText(bigRepositories: List<VirtualFile>): String {
    val repositoriesLimit = 3
    val result = bigRepositories.map { it.name }.sorted().take(repositoriesLimit).joinToString(", ") { "'$it'" }
    if (bigRepositories.size > repositoriesLimit) {
      return "$result, ..."
    }
    return result
  }

  override fun actionPerformed(e: AnActionEvent) {
    VcsLogUsageTriggerCollector.triggerUsage(e, this)

    val data = e.getRequiredData(VcsLogInternalDataKeys.LOG_DATA)

    var resumed = false
    for (root in getBigRepositories(data)) {
      resumed = resumed or VcsLogBigRepositoriesList.getInstance().removeRepository(root)
    }
    if (resumed) (data.index as? VcsLogModifiableIndex)?.scheduleIndex(false)
  }

  private fun getBigRepositories(data: VcsLogData): List<VirtualFile> {
    return VcsLogPersistentIndex.getRootsForIndexing(data.logProviders).filter {
      VcsLogBigRepositoriesList.getInstance().isBig(it)
    }
  }
}