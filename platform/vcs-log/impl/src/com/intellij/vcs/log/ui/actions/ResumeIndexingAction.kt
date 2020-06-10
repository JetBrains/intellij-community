// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.ui.actions

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.vcs.log.VcsLogBundle
import com.intellij.vcs.log.data.index.VcsLogBigRepositoriesList
import com.intellij.vcs.log.data.index.VcsLogIndex
import com.intellij.vcs.log.data.index.VcsLogModifiableIndex
import com.intellij.vcs.log.data.index.VcsLogPersistentIndex
import com.intellij.vcs.log.impl.VcsLogSharedSettings
import com.intellij.vcs.log.statistics.VcsLogUsageTriggerCollector
import com.intellij.vcs.log.ui.VcsLogInternalDataKeys
import com.intellij.vcs.log.util.VcsLogUtil

class ResumeIndexingAction : DumbAwareAction() {
  override fun update(e: AnActionEvent) {
    val data = e.getData(VcsLogInternalDataKeys.LOG_DATA)
    val project = e.project
    if (data == null || project == null || !VcsLogSharedSettings.isIndexSwitchedOn(project)) {
      e.presentation.isEnabledAndVisible = false
      return
    }
    val rootsForIndexing = VcsLogPersistentIndex.getRootsForIndexing(data.logProviders)
    if (rootsForIndexing.isEmpty()) {
      e.presentation.isEnabledAndVisible = false
      return
    }

    val scheduledForIndexing = rootsForIndexing.filter { it.isScheduledForIndexing(data.index) }
    val bigRepositories = rootsForIndexing.filter { it.isBig() }
    e.presentation.isEnabledAndVisible = (bigRepositories.isNotEmpty() || scheduledForIndexing.isNotEmpty())

    val vcsDisplayName = VcsLogUtil.getVcsDisplayName(project, rootsForIndexing.map { data.getLogProvider(it) })
    if (scheduledForIndexing.isNotEmpty()) {
      e.presentation.text = VcsLogBundle.message("action.title.pause.indexing", vcsDisplayName)
      e.presentation.description = VcsLogBundle.message("action.description.is.scheduled", getText(scheduledForIndexing))
      e.presentation.icon = AllIcons.Process.ProgressPauseSmall
    }
    else {
      e.presentation.text = VcsLogBundle.message("action.title.resume.indexing", vcsDisplayName)
      e.presentation.description = VcsLogBundle.message("action.description.was.paused", getText(bigRepositories))
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
    val rootsForIndexing = VcsLogPersistentIndex.getRootsForIndexing(data.logProviders)
    if (rootsForIndexing.isEmpty()) return

    if (rootsForIndexing.any { it.isScheduledForIndexing(data.index) }) {
      rootsForIndexing.filter { !it.isBig() }.forEach { VcsLogBigRepositoriesList.getInstance().addRepository(it) }
    }
    else {
      var resumed = false
      for (root in rootsForIndexing.filter { it.isBig() }) {
        resumed = resumed or VcsLogBigRepositoriesList.getInstance().removeRepository(root)
      }
      if (resumed) (data.index as? VcsLogModifiableIndex)?.scheduleIndex(false)
    }
  }

  private fun VirtualFile.isBig(): Boolean = VcsLogBigRepositoriesList.getInstance().isBig(this)
  private fun VirtualFile.isScheduledForIndexing(index: VcsLogIndex): Boolean = index.isIndexingEnabled(this) &&
                                                                                !index.isIndexed(this)
}