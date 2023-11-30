package com.intellij.driver.sdk

import com.intellij.driver.client.Driver
import com.intellij.driver.client.service
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

fun Driver.waitForIndicators(timeout: Duration) {
  waitFor(timeout) {
    isProjectOpened() && !areIndicatorsVisible(singleProject())
  }
}

fun Driver.waitForIndicators(project: Project, timeout: Duration) {
  waitFor(timeout) {
    !areIndicatorsVisible(project)
  }
}