package com.intellij.driver.sdk

import com.intellij.driver.client.Driver
import com.intellij.driver.client.service
import java.time.Instant
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

fun Driver.getProgressIndicators(project: Project): List<StatusBar.TaskInfoPair> {
  return withContext {
    val ideFrame = service<WindowManager>().getIdeFrame(project)
    val statusBar = ideFrame?.getStatusBar() ?: return@withContext emptyList()
    statusBar.getBackgroundProcesses()
  }
}

fun Driver.areIndicatorsVisible(project: Project): Boolean {
  if (service<DumbService>(project).isDumb()) return true

  return getProgressIndicators(project).isNotEmpty()
}

/**
 * !!! ATTENTION !!!
 *
 * The only guarantee you have after this method is that reference to Project won't be null.
 * The UI, Project View, services and everything else might not be yet initialized.
 * Use [waitForIndicators] instead if you're not 100% sure otherwise you might get flaky test.
 * Also, you can avoid calling this method before [waitForIndicators] since it also waits for an open project.
 */
fun Driver.waitForProjectOpen(timeout: Duration = 1.minutes) {
  waitFor("Project is opened", timeout) {
    isProjectOpened()
  }
}

/**
 * Method waits till a project is opened and there are no indicators for 10 seconds.
 */
fun Driver.waitForIndicators(project: Project, timeout: Duration) {
  waitForIndicators({ project }, timeout)
}

/**
 * Method waits till a project is opened and there are no indicators for 10 seconds.
 */
fun Driver.waitForIndicators(timeout: Duration) {
  waitForProjectOpen(timeout)
  waitForIndicators(::singleProject, timeout)
}

/**
 * Method waits till a project is opened and there are no indicators for 10 seconds.
 */
private fun Driver.waitForIndicators(projectGet: () -> Project, timeout: Duration) {
  var smartLongEnoughStart: Instant? = null

  waitFor("Indicators", timeout) {
    val project = runCatching { projectGet.invoke() }.getOrNull()
    if (project == null || !isProjectOpened(project) || areIndicatorsVisible(project)) {
      smartLongEnoughStart = null
      return@waitFor false
    }

    val start = smartLongEnoughStart
    if (start == null) {
      smartLongEnoughStart = Instant.now()
    }
    else {
      val now = Instant.now()
      if (start.plusSeconds(10).isBefore(now)) {
        return@waitFor true // we are smart long enough
      }
    }

    false
  }
}