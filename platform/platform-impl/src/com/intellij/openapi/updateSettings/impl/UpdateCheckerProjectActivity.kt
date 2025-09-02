// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.updateSettings.impl

import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.extensions.ExtensionNotApplicableException
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.coroutineToIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

private class UpdateCheckerProjectActivity : ProjectActivity {
  private val isStarted = AtomicBoolean(false)

  init {
    val app = ApplicationManager.getApplication()
    if (app.isCommandLine || app.isHeadlessEnvironment || app.isUnitTestMode) {
      throw ExtensionNotApplicableException.create()
    }
  }

  override suspend fun execute(project: Project) {
    if (isStarted.getAndSet(true)) {
      return
    }

    val current = ApplicationInfo.getInstance().build
    UpdateCheckerService.checkIfPreviousUpdateFailed(current)
    UpdateCheckerService.showWhatsNew(project, current)
    UpdateCheckerService.showSnapUpdateNotification(project, current)

    UpdateCheckerService.pruneUpdateSettings()
    UpdateCheckerService.showUpdatedPluginsNotification(project)

    withContext(Dispatchers.IO) {
      coroutineToIndicator {
        @Suppress("UsagesOfObsoleteApi")
        UpdateCheckerService.deleteOldApplicationDirectories(ProgressManager.getGlobalProgressIndicator())
      }

      UpdateInstaller.cleanupPatch()
    }
  }
}
