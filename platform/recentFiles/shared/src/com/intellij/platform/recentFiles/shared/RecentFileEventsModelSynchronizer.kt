// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.recentFiles.shared

import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.platform.util.coroutines.childScope
import kotlinx.coroutines.launch

private val LOG by lazy { fileLogger() }

internal class RecentFileEventsModelSynchronizer : ProjectActivity {
  override suspend fun execute(project: Project) {
    if (!doesProcessHostRecentFilesModel()) return
    val synchronizationScope = RecentFilesCoroutineScopeProvider.getInstanceAsync(project).coroutineScope.childScope("Recent file events -> Recent files list synchronisation")

    val recentFilesModel = RecentFilesModel.getInstanceAsync(project)
    synchronizationScope.launch {
      LOG.debug("Subscribe to recently opened files updates")
      recentFilesModel.subscribeToRecentFileEvents(RecentFileKind.RECENTLY_OPENED)
    }
    synchronizationScope.launch {
      LOG.debug("Subscribe to recently edited files updates")
      recentFilesModel.subscribeToRecentFileEvents(RecentFileKind.RECENTLY_EDITED)
    }
    synchronizationScope.launch {
      LOG.debug("Subscribe to recently opened unpinned files updates")
      recentFilesModel.subscribeToRecentFileEvents(RecentFileKind.RECENTLY_OPENED_UNPINNED)
    }
  }
}
