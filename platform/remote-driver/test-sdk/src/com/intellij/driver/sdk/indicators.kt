package com.intellij.driver.sdk

import com.intellij.driver.client.Driver
import com.intellij.driver.client.service

fun Driver.getProgressIndicators(project: Project): List<StatusBar.TaskInfoPair> {
  val ideFrame = service<WindowManager>().getIdeFrame(project)
  val statusBar = ideFrame?.statusBar ?: return emptyList()
  return statusBar.backgroundProcesses
}

fun Driver.areIndicatorsVisible(project: Project): Boolean {
  if (service<DumbService>(project).isDumb()) return true

  return getProgressIndicators(project).isNotEmpty()
}
