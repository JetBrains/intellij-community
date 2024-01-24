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

fun Driver.waitForProjectOpen(timeout: Duration = 1.minutes) {
  waitFor(timeout) {
    isProjectOpened()
  }
}

fun Driver.waitForIndicators(project: Project, timeout: Duration) {
  waitForIndicators({ project }, timeout)
}

fun Driver.waitForIndicators(timeout: Duration) {
  waitForIndicators(::singleProject, timeout)
}

private fun Driver.waitForIndicators(projectGet: () -> Project, timeout: Duration) {
  var smartLongEnoughStart: Instant? = null

  waitFor(timeout) {
    if (!isProjectOpened() || areIndicatorsVisible(projectGet.invoke())) {
      smartLongEnoughStart = null
      return@waitFor false
    }

    val start = smartLongEnoughStart
    if (start == null) {
      smartLongEnoughStart = Instant.now()
    }
    else {
      val now = Instant.now()
      if (start.plusSeconds(3).isBefore(now)) {
        return@waitFor true // we are smart long enough
      }
    }

    false
  }
}