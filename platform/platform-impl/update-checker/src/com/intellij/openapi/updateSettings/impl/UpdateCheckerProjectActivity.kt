// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.updateSettings.impl

import com.intellij.ide.OldDirectoryCleaner
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.InitialConfigImportState
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.extensions.ExtensionNotApplicableException
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.coroutineToIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.util.text.DateFormatUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

internal class UpdateCheckerProjectActivity : ProjectActivity {
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
      deleteOldApplicationDirectories()

      UpdateInstaller.cleanupPatch()
    }
  }
}

private const val OLD_DIRECTORIES_SCAN_SCHEDULED = "ide.updates.old.dirs.scan.scheduled"
private const val OLD_DIRECTORIES_SCAN_DELAY_DAYS = 7
private const val OLD_DIRECTORIES_SHELF_LIFE_DAYS = 180

suspend fun deleteOldApplicationDirectories() {
  val propertyService = serviceAsync<PropertiesComponent>()
  if (InitialConfigImportState.isConfigImported()) {
    val scheduledAt = System.currentTimeMillis() + TimeUnit.DAYS.toMillis(OLD_DIRECTORIES_SCAN_DELAY_DAYS.toLong())
    UpdateCheckerService.LOG.info("scheduling old directories scan after " + DateFormatUtil.formatDateTime(scheduledAt))
    propertyService.setValue(OLD_DIRECTORIES_SCAN_SCHEDULED, scheduledAt.toString())
    OldDirectoryCleaner.Stats.scheduled()
  }
  else {
    val scheduledAt = propertyService.getLong(OLD_DIRECTORIES_SCAN_SCHEDULED, 0L)
    var now: Long = 0
    if (scheduledAt != 0L && (System.currentTimeMillis().also { now = it }) >= scheduledAt) {
      OldDirectoryCleaner.Stats.started(TimeUnit.MILLISECONDS.toDays(now - scheduledAt).toInt() + OLD_DIRECTORIES_SCAN_DELAY_DAYS)
      UpdateCheckerService.LOG.info("starting old directories scan")
      val expireAfter = now - TimeUnit.DAYS.toMillis(OLD_DIRECTORIES_SHELF_LIFE_DAYS.toLong())

      coroutineToIndicator {
        @Suppress("UsagesOfObsoleteApi")
        OldDirectoryCleaner(expireAfter).seekAndDestroy(null, ProgressManager.getGlobalProgressIndicator())
      }
      propertyService.unsetValue(OLD_DIRECTORIES_SCAN_SCHEDULED)
      UpdateCheckerService.LOG.info("old directories scan complete")
    }
  }
}
