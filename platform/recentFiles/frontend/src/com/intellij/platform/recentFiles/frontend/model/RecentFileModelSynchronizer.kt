// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.recentFiles.frontend.model

import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.platform.recentFiles.shared.RecentFileKind
import com.intellij.platform.recentFiles.shared.RecentFilesCoroutineScopeProvider
import com.intellij.platform.util.coroutines.childScope
import kotlinx.coroutines.launch

private val LOG by lazy { fileLogger() }

internal class RecentFileModelSynchronizer : ProjectActivity {
  override suspend fun execute(project: Project) {
    val synchronizationScope = RecentFilesCoroutineScopeProvider.getInstanceAsync(project).coroutineScope.childScope("RecentFilesModel frontend/backend synchronisation")

    val frontendRecentFilesModel = FrontendRecentFilesModel.getInstanceAsync(project)
    synchronizationScope.launch {
      LOG.debug("Subscribe to backend recently opened files updates")
      frontendRecentFilesModel.subscribeToBackendRecentFilesUpdates(RecentFileKind.RECENTLY_OPENED)
    }
    synchronizationScope.launch {
      LOG.debug("Subscribe to backend recently edited files updates")
      frontendRecentFilesModel.subscribeToBackendRecentFilesUpdates(RecentFileKind.RECENTLY_EDITED)
    }
    synchronizationScope.launch {
      LOG.debug("Subscribe to backend recently opened unpinned files updates")
      frontendRecentFilesModel.subscribeToBackendRecentFilesUpdates(RecentFileKind.RECENTLY_OPENED_UNPINNED)
    }

    synchronizationScope.launch {
      LOG.debug("Fetch initial recently opened files data")
      frontendRecentFilesModel.fetchInitialData(RecentFileKind.RECENTLY_OPENED, project)
    }
    synchronizationScope.launch {
      LOG.debug("Fetch initial recently edited files data")
      frontendRecentFilesModel.fetchInitialData(RecentFileKind.RECENTLY_EDITED, project)
    }
    synchronizationScope.launch {
      LOG.debug("Fetch initial recently opened unpinned files data")
      frontendRecentFilesModel.fetchInitialData(RecentFileKind.RECENTLY_OPENED_UNPINNED, project)
    }
  }
}