package com.intellij.driver.sdk

import com.intellij.driver.client.Driver
import com.intellij.driver.client.Remote
import com.intellij.driver.client.service
import com.intellij.driver.model.RdTarget

@Remote("com.intellij.openapi.project.ProjectManager")
interface ProjectManager {
  fun getOpenProjects(): Array<Project>
}

fun Driver.getOpenProjects(): List<Project> {
  return service<ProjectManager>().getOpenProjects().toList()
}

fun Driver.singleProject(rdTarget: RdTarget = RdTarget.DEFAULT): Project {
  return withContext {
    service<ProjectManager>(rdTarget).getOpenProjects().singleOrNull() ?: throw IllegalStateException("No projects are opened")
  }
}

fun Driver.isProjectOpened(): Boolean {
  return withContext {
    val openProjects = service<ProjectManager>().getOpenProjects()

    if (openProjects.size == 1
        && openProjects[0].isInitialized()) {
      val ideFrame = getIdeFrame(openProjects.single())
      return@withContext ideFrame?.getComponent()?.isVisible() == true
    }
    return@withContext false
  }
}