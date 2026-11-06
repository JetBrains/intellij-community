// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.updateSettings.impl

import com.intellij.diagnostic.rethrowControlFlowException
import com.intellij.ide.AppLifecycleListener
import com.intellij.ide.IdeBundle
import com.intellij.ide.RecentProjectsManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.ex.ApplicationEx
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.ProjectManager
import com.intellij.util.concurrency.annotations.RequiresEdt
import org.jetbrains.annotations.ApiStatus
import java.io.IOException
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.io.path.isDirectory

private val LOG = logger<MigrationOfferContent>()

// after the timeout, `open` continues and starts the Unified IDE; only the log of its exit code is lost
private const val OPEN_TIMEOUT_SECONDS = 5L

/**
 * The arguments that start the Unified IDE after "Restart to Update".
 * [sourceProduct] is the product code of this IDE. [projects] are the open projects.
 */
@ApiStatus.Internal
fun migrationLaunchArguments(sourceProduct: String, projects: List<Path>): List<String> =
  listOf(
    "-Dide.migration.version=1",
    "-Dide.migration.reason=migrate",
    "-Dide.migration.source.product=${sourceProduct}",
  ) + projects.map { it.toString() }

/** The macOS command that starts a new instance of [app] with [arguments]. Each value is a separate argument. */
@ApiStatus.Internal
fun macOpenCommand(app: Path, arguments: List<String>): List<String> =
  listOf("/usr/bin/open", "-n", "-a", app.toString(), "--args") + arguments

/**
 * The action of "Restart to Update" for an installed migration distribution.
 * The IDE saves and exits as usual, and the user can cancel the exit. Only a completed exit starts the Unified IDE.
 * When the Unified IDE is removed, it deletes the state and shows an error, so that the next update check offers the migration again.
 */
@RequiresEdt
internal fun restartToMigration() {
  val store = migrationStateStore()
  val state = store.get()
  if (state == null || state.status != MigrationStatus.INSTALLED) {
    LOG.info("no installed migration distribution to start: ${state?.status}")
    return
  }
  if (!state.app.isDirectory()) {
    LOG.info("the Unified IDE does not exist: ${state.app}")
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
    startMigrationProcess(macOpenCommand(state.app, migrationLaunchArguments(sourceProduct, projects)))
      ?.let { waitForMigrationOpen(it, OPEN_TIMEOUT_SECONDS) }
  }
  catch (e: Exception) {
    rethrowControlFlowException(e)
    LOG.warn("cannot start ${state.app}", e)
  }
}

/** Starts [command] and logs it. A failure is logged and gives `null`. */
private fun startMigrationProcess(command: List<String>): Process? {
  LOG.info("starting the Unified IDE: ${command}")
  try {
    return ProcessBuilder(command).redirectOutput(ProcessBuilder.Redirect.DISCARD).start()
  }
  catch (e: IOException) {
    LOG.warn("cannot start the Unified IDE", e)
    return null
  }
}

/** Waits up to [timeoutSeconds] for the `open` [process] and logs a failure. Returns `true` when `open` ends with the exit code 0. */
private fun waitForMigrationOpen(process: Process, timeoutSeconds: Long): Boolean {
  if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
    LOG.warn("the start of the Unified IDE does not end in ${timeoutSeconds} s")
    return false
  }
  val exitCode = process.exitValue()
  if (exitCode != 0) {
    val stderr = process.errorStream.bufferedReader().use { it.readText() }.trim()
    LOG.warn("cannot start the Unified IDE: exit code ${exitCode}, ${stderr}")
    return false
  }
  return true
}
