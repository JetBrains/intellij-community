// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.ui.actions

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.vcs.log.VcsLogBundle
import com.intellij.vcs.log.data.VcsLogData
import com.intellij.vcs.log.data.index.*
import com.intellij.vcs.log.impl.VcsLogSharedSettings
import com.intellij.vcs.log.statistics.VcsLogUsageTriggerCollector
import com.intellij.vcs.log.ui.VcsLogInternalDataKeys
import com.intellij.vcs.log.util.VcsLogUtil

class ResumeIndexingAction : DumbAwareAction() {
  override fun update(e: AnActionEvent) {
    val data = e.getData(VcsLogInternalDataKeys.LOG_DATA)
    val project = e.project
    val index = data?.index as? VcsLogModifiableIndex
    if (data == null || project == null || !VcsLogSharedSettings.isIndexSwitchedOn(project) || index == null) {
      e.presentation.isEnabledAndVisible = false
      return
    }

    if (!VcsLogData.isIndexSwitchedOnInRegistry()) {
      val availableIndexers = VcsLogPersistentIndex.getAvailableIndexers(data.logProviders)
      if (availableIndexers.isEmpty()) {
        e.presentation.isEnabledAndVisible = false
        return
      }
      val vcsDisplayName = VcsLogUtil.getVcsDisplayName(project, availableIndexers.keys.map { data.getLogProvider(it) })
      e.presentation.text = VcsLogBundle.message("action.title.enable.indexing", vcsDisplayName)
      e.presentation.description = VcsLogBundle.message("action.description.was.disabled", vcsDisplayName)
      e.presentation.icon = AllIcons.Process.ProgressResumeSmall
      return
    }

    val rootsForIndexing = index.indexingRoots
    if (rootsForIndexing.isEmpty()) {
      e.presentation.isEnabledAndVisible = false
      return
    }

    val rootsScheduledForIndexing = rootsForIndexing.filter { data.index.isScheduledForIndexing(it) }
    val rootsWithPausedIndexing = rootsForIndexing.filter { isIndexingPausedFor(it) }
    e.presentation.isEnabledAndVisible = (rootsWithPausedIndexing.isNotEmpty() || rootsScheduledForIndexing.isNotEmpty())

    val vcsDisplayName = VcsLogUtil.getVcsDisplayName(project, rootsForIndexing.map { data.getLogProvider(it) })
    if (rootsScheduledForIndexing.isNotEmpty()) {
      e.presentation.text = VcsLogBundle.message("action.title.pause.indexing", vcsDisplayName)
      e.presentation.description = VcsLogBundle.message("action.description.is.scheduled", getText(rootsScheduledForIndexing))
      e.presentation.icon = AllIcons.Process.ProgressPauseSmall
    }
    else {
      e.presentation.text = VcsLogBundle.message("action.title.resume.indexing", vcsDisplayName)
      e.presentation.description = VcsLogBundle.message("action.description.was.paused", getText(rootsWithPausedIndexing))
      e.presentation.icon = AllIcons.Process.ProgressResumeSmall
    }
  }

  private fun getText(repositories: List<VirtualFile>): String {
    val repositoriesLimit = 3
    val result = repositories.map { it.name }.sorted().take(repositoriesLimit).joinToString(", ") { "'$it'" }
    if (repositories.size > repositoriesLimit) {
      return "$result, ..."
    }
    return result
  }

  override fun actionPerformed(e: AnActionEvent) {
    VcsLogUsageTriggerCollector.triggerUsage(e, this)

    val data = e.getRequiredData(VcsLogInternalDataKeys.LOG_DATA)

    if (!VcsLogData.isIndexSwitchedOnInRegistry()) {
      val rootsForIndexing = VcsLogPersistentIndex.getAvailableIndexers(data.logProviders).keys
      rootsForIndexing.forEach { VcsLogBigRepositoriesList.getInstance().removeRepository(it) }
      VcsLogData.getIndexingRegistryValue().setValue(true)
    }

    val index = data.index as? VcsLogModifiableIndex ?: return
    if (index.indexingRoots.isEmpty()) return

    index.toggleIndexing()
  }

  override fun getActionUpdateThread() = ActionUpdateThread.BGT
}
