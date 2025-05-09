// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.project

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.util.AwaitCancellationAndInvoke
import com.intellij.util.awaitCancellationAndInvoke
import kotlinx.coroutines.CoroutineScope
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages the mapping between [Project]s and their unique [ProjectId]s.
 *
 * @see findProject
 * @see projectId
 */
@OptIn(AwaitCancellationAndInvoke::class)
@Service(Service.Level.APP)
internal class ProjectIdsStorage(cs: CoroutineScope) {
  private val projectToIds = ConcurrentHashMap<Project, ProjectId>()
  private val idsToProject = ConcurrentHashMap<ProjectId, Project>()

  init {
    cs.awaitCancellationAndInvoke {
      projectToIds.clear()
      idsToProject.clear()
    }
  }

  fun registerProject(project: Project): ProjectId {
    val projectId = ProjectId.create()
    projectToIds[project] = projectId
    idsToProject[projectId] = project

    return projectId
  }

  fun unregisterProject(project: Project) {
    val currentId = projectToIds.remove(project)
    if (currentId != null) {
      idsToProject.remove(currentId)
    }
  }

  fun getProjectId(project: Project): ProjectId? {
    return projectToIds[project]
  }

  fun findProject(projectId: ProjectId): Project? {
    return idsToProject[projectId]
  }

  /**
   * This function is needed for remote dev, so client may set backend's project id to the local one.
   */
  fun setProjectId(project: Project, newProjectId: ProjectId) {
    val oldId = projectToIds.put(project, newProjectId)
    idsToProject.remove(oldId)
    idsToProject[newProjectId] = project
  }

  companion object {
    @JvmStatic
    fun getInstance(): ProjectIdsStorage = service<ProjectIdsStorage>()
  }
}