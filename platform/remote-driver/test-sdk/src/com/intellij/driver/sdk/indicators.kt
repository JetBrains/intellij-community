package com.intellij.driver.sdk

import com.intellij.driver.client.Driver

fun Driver.getProgressIndicators(project: Project): List<StatusBar.TaskInfoPair> {
  val ideFrame = service(WindowManager::class).getIdeFrame(project)
  val statusBar = ideFrame?.statusBar ?: return emptyList()
  return statusBar.backgroundProcesses
}

fun Driver.areIndicatorsVisible(project: Project): Boolean {
  if (service(DumbService::class, project).isDumb()) return true

  return getProgressIndicators(project).isNotEmpty()
}
