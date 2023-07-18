package com.intellij.driver.sdk

import com.intellij.driver.client.Driver

fun Driver.singleProject(): Project {
  return service(ProjectManager::class).openProjects.single()
}

fun Driver.getIdeFrame(project: Project): IdeFrame? {
  return service(WindowManager::class).getIdeFrame(project)
}

fun Driver.isProjectOpened(): Boolean {
  val projectManager = service(ProjectManager::class)
  val openProjects = projectManager.openProjects

  if (openProjects.size == 1
      && openProjects[0].isInitialized) {
    val ideFrame = getIdeFrame(openProjects.single())
    return ideFrame?.component?.isVisible() == true
  }

  return false
}