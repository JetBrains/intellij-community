package com.intellij.driver.sdk

import com.intellij.driver.client.Driver
import com.intellij.driver.client.Remote
import com.intellij.driver.client.service
import com.intellij.driver.model.RdTarget

@Remote("com.intellij.openapi.project.ProjectManager")
interface ProjectManager {
  fun getOpenProjects(): Array<Project>
  fun getDefaultProject(): Project
}

fun Driver.getOpenProjects(): List<Project> {
  return service<ProjectManager>().getOpenProjects().toList()
}

fun Driver.getDefaultProject(): Project {
  return service<ProjectManager>().getDefaultProject()
}

fun Driver.singleProject(rdTarget: RdTarget = RdTarget.DEFAULT): Project {
  return withContext {
    service<ProjectManager>(rdTarget).getOpenProjects().singleOrNull() ?: throw IllegalStateException("No projects are opened")
  }
}

fun Driver.isProjectOpened(project: Project? = null): Boolean {
  return withContext {
    val projectToCheck = project ?: getOpenProjects().singleOrNull()

    if (projectToCheck?.isInitialized() == true) {
      val ideFrame = getIdeFrame(projectToCheck)
      return@withContext ideFrame?.getComponent()?.isVisible() == true
    }
    return@withContext false
  }
}