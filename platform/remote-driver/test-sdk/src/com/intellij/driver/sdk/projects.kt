package com.intellij.driver.sdk

import com.intellij.driver.client.Driver

fun Driver.singleProject(): Project {
  return withContext {
    service(ProjectManager::class).openProjects.single()
  }
}

fun Driver.getIdeFrame(project: Project): IdeFrame? {
  return service(WindowManager::class).getIdeFrame(project)
}

fun Driver.isProjectOpened(): Boolean {
  return withContext {
    val openProjects = service(ProjectManager::class).openProjects

    if (openProjects.size == 1
        && openProjects[0].isInitialized) {
      val ideFrame = getIdeFrame(openProjects.single())
      return@withContext ideFrame?.component?.isVisible() == true
    }
    return@withContext false
  }
}

fun Driver.isProjectInitializationAndIndexingFinished(project: Project): Boolean {
  return withContext {
    service(ProjectInitializationDiagnosticService::class, project).isProjectInitializationAndIndexingFinished()
  }
}