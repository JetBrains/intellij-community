// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.committed

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.committed.CacheSettingsDialog.showSettingsDialog

private fun Project.getCommittedChangesCache(): CommittedChangesCache = CommittedChangesCache.getInstance(this)

class RefreshIncomingChangesAction : DumbAwareAction() {
  override fun update(e: AnActionEvent) {
    e.presentation.isEnabled = e.project?.getCommittedChangesCache()?.isRefreshingIncomingChanges == false
  }

  override fun actionPerformed(e: AnActionEvent) = doRefresh(e.project!!)

  companion object {
    @JvmStatic
    fun doRefresh(project: Project) {
      val cache = project.getCommittedChangesCache()

      cache.hasCachesForAnyRoot { hasCaches ->
        if (!hasCaches && !showSettingsDialog(project)) return@hasCachesForAnyRoot

        cache.refreshAllCachesAsync(true, false)
        cache.refreshIncomingChangesAsync()
      }
    }
  }
}