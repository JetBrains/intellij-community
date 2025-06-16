// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.recentFiles.backend

import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.platform.recentFiles.shared.RecentFileKind
import com.intellij.platform.recentFiles.shared.RecentFilesCoroutineScopeProvider
import com.intellij.platform.util.coroutines.childScope
import kotlinx.coroutines.launch

private val LOG by lazy { fileLogger() }

internal class BackendRecentFileModelSynchronizer : ProjectActivity {
  override suspend fun execute(project: Project) {
    val synchronizationScope = RecentFilesCoroutineScopeProvider.getInstanceAsync(project).coroutineScope.childScope("Recent file events -> Recent files list synchronisation")

    val backendRecentFilesModel = BackendRecentFilesModel.getInstanceAsync(project)
    synchronizationScope.launch {
      LOG.debug("Subscribe to backend recently opened files updates")
      backendRecentFilesModel.subscribeToBackendRecentFilesUpdates(RecentFileKind.RECENTLY_OPENED)
    }
    synchronizationScope.launch {
      LOG.debug("Subscribe to backend recently edited files updates")
      backendRecentFilesModel.subscribeToBackendRecentFilesUpdates(RecentFileKind.RECENTLY_EDITED)
    }
    synchronizationScope.launch {
      LOG.debug("Subscribe to backend recently opened unpinned files updates")
      backendRecentFilesModel.subscribeToBackendRecentFilesUpdates(RecentFileKind.RECENTLY_OPENED_UNPINNED)
    }
  }
}