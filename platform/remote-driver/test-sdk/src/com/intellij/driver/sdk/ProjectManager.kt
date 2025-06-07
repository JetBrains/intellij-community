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
  val project = withContext {
    service<ProjectManager>(rdTarget).getOpenProjects().singleOrNull() ?: service<LightEditService>().getProject()
  }
  if (project == null) {
    throw IllegalStateException("No projects are opened")
  }
  return project
}

fun Driver.isProjectOpened(project: Project? = null): Boolean {
  return withContext {
    val projectToCheck = project ?: getOpenProjects().singleOrNull() ?: service<LightEditService>().getProject()

    if (projectToCheck?.isInitialized() == true) {
      val ideFrame = getIdeFrame(projectToCheck)
      return@withContext ideFrame?.getComponent()?.isShowing() == true
    }
    return@withContext false
  }
}