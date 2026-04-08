package com.intellij.driver.sdk

import com.intellij.driver.client.Driver
import com.intellij.driver.client.service
import com.intellij.openapi.diagnostic.logger
import java.time.Instant
import kotlin.collections.emptyList
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

fun Driver.getProgressIndicators(project: Project): List<Pair<TaskInfo?, ProgressModel?>> {
  return withContext {
    val ideFrame = service<WindowManager>().getIdeFrame(project)
    val statusBar = ideFrame?.getStatusBar() ?: return@withContext emptyList()
    statusBar.getBackgroundProcessModels()
  }
}

private fun Driver.logProgressIndicators(project: Project) {
  return withContext {
    val ideFrame = service<WindowManager>().getIdeFrame(project)
    val statusBar = ideFrame?.getStatusBar() ?: return@withContext
    logger<Driver>().info("Running indicators were detected:\n" +
                          "${statusBar.getBackgroundProcessModels().map { it.first?.getTitle() ?: "Task with no title" }}")
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
fun Driver.waitForIndicators(project: Project, timeout: Duration, waitSmartLongEnough: Boolean = true) {
  waitForIndicators({ project }, timeout, waitSmartLongEnough = waitSmartLongEnough)
}

/**
 * Method waits till a project is opened and there are no indicators for 10 seconds.
 */
fun Driver.waitForIndicators(timeout: Duration, waitSmartLongEnough: Boolean = true) {
  waitForProjectOpen(timeout)
  waitForIndicators(::singleProject, timeout, waitSmartLongEnough = waitSmartLongEnough)
}

/**
 * Method waits till a project is opened and there are no indicators for 10 seconds.
 */
internal fun Driver.waitForIndicators(projectGet: () -> Project?, timeout: Duration, waitSmartLongEnough: Boolean = true) {
  var smartLongEnoughStart: Instant? = null

  waitFor("Indicators with waitSmartLongEnough=$waitSmartLongEnough", timeout) {
    val project = runCatching { projectGet.invoke() }.getOrNull()
    val projectReady = (project != null && isProjectOpened(project))
    val indicatorsVisible = projectReady && areIndicatorsVisible(project)
    if (!projectReady) {
      logger<Driver>().info("The project is not opened.")
    }
    if (indicatorsVisible) {
      logProgressIndicators(project)
    }
    if (!projectReady || indicatorsVisible) {
      smartLongEnoughStart = null
      return@waitFor false
    }

    if (waitSmartLongEnough) {
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
    else {
      true
    }
  }
}