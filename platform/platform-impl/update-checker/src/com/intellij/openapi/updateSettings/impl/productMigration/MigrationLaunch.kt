// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:OptIn(LowLevelLocalMachineAccess::class)

package com.intellij.openapi.updateSettings.impl.productMigration

import com.intellij.diagnostic.rethrowControlFlowException
import com.intellij.ide.AppLifecycleListener
import com.intellij.ide.IdeBundle
import com.intellij.ide.RecentProjectsManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.ex.ApplicationEx
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.application.productMigration.MigrationLaunchReason
import com.intellij.openapi.application.productMigration.MigrationState
import com.intellij.openapi.application.productMigration.MigrationStatus
import com.intellij.openapi.application.productMigration.macOpenCommand
import com.intellij.openapi.application.productMigration.migrationLaunchArguments
import com.intellij.openapi.application.productMigration.readInstalledProductInfo
import com.intellij.openapi.application.productMigration.startMigrationLauncher
import com.intellij.openapi.application.productMigration.startMigrationProcess
import com.intellij.openapi.application.productMigration.waitForMigrationOpen
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.updateSettings.impl.UpdateChecker
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.system.LowLevelLocalMachineAccess
import com.intellij.util.system.OS
import java.io.IOException
import kotlin.io.path.isDirectory

private val LOG = logger<MigrationOfferContent>()

// after the timeout, `open` continues and starts the Unified IDE; only the log of its exit code is lost
private const val OPEN_TIMEOUT_SECONDS = 5L

/**
 * The action of "Restart to Update" for an installed migration distribution.
 * The IDE saves and exits as usual, and the user can cancel the exit. Only a completed exit starts the Unified IDE.
 * When the Unified IDE is removed, it deletes the state and shows an error, so that the next update check offers the migration again.
 * On Windows, a Unified IDE with no launcher counts as removed, because the IDE cannot start it.
 */
@RequiresEdt
internal fun restartToMigration() {
  val store = migrationStateStore()
  val state = store.get()
  if (state == null || state.status != MigrationStatus.INSTALLED) {
    LOG.info("no installed migration distribution to start: ${state?.status}")
    return
  }
  if (!state.app.isDirectory() || OS.CURRENT == OS.Windows && readInstalledProductInfo(state.app)?.launcher == null) {
    LOG.info("the Unified IDE does not exist or has no launcher: ${state.app}")
    store.clear()
    UpdateChecker.getNotificationGroupForIdeUpdateResults()
      .createNotification(IdeBundle.message("updates.migration.error.title"),
                          IdeBundle.message("updates.migration.error.app.missing", state.app.toString()),
                          NotificationType.ERROR)
      .setDisplayId("ide.migration.app.missing")
      .notify(null)
    return
  }

  val app = ApplicationManagerEx.getApplicationEx()
  app.invokeLater {
    val connection = app.messageBus.connect()
    connection.subscribe(AppLifecycleListener.TOPIC, object : AppLifecycleListener {
      override fun appWillBeClosed(isRestart: Boolean) {
        connection.disconnect()
        if (!isRestart) {
          startMigrationApp(state)
        }
      }
    })
    try {
      app.exit(ApplicationEx.EXIT_CONFIRMED or ApplicationEx.SAVE)
    }
    finally {
      // a cancelled exit returns here, and a later exit must not start the Unified IDE
      if (!app.isExitInProgress) {
        connection.disconnect()
      }
    }
  }
}

/** Runs after the settings and the documents are saved, and after the last check that can cancel the exit. */
private fun startMigrationApp(state: MigrationState) {
  try {
    val recentProjects = RecentProjectsManager.getInstance()
    val projects = ProjectManager.getInstance().openProjects.mapNotNull { recentProjects.getProjectPath(it) }.distinct()
    try {
      migrationStateStore().set(state.withStatus(MigrationStatus.MOVED))
    }
    catch (e: IOException) {
      LOG.warn("cannot write the migration state", e)
    }

    val sourceProduct = ApplicationInfo.getInstance().build.productCode
    val arguments = migrationLaunchArguments(MigrationLaunchReason.MIGRATE, sourceProduct, projects.map { it.toString() })
    when (OS.CURRENT) {
      OS.macOS -> startMigrationProcess(macOpenCommand(state.app, arguments))?.let { waitForMigrationOpen(it, OPEN_TIMEOUT_SECONDS) }
      OS.Windows -> {
        // no wait: a launcher that cannot start the IDE shows its own message and does not exit
        val launcher = readInstalledProductInfo(state.app)?.launcher
        if (launcher == null) LOG.warn("no launcher in ${state.app}") else startMigrationLauncher(launcher, arguments)
      }
      OS.Linux, OS.FreeBSD, OS.OHOS, OS.Other -> LOG.warn("no start of the Unified IDE for ${OS.CURRENT}")
    }
  }
  catch (e: Exception) {
    rethrowControlFlowException(e)
    LOG.warn("cannot start ${state.app}", e)
  }
}
