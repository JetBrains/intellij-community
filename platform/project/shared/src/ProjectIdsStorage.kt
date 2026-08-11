// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.project

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.util.AwaitCancellationAndInvoke
import com.intellij.util.awaitCancellationAndInvoke
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.ApiStatus
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
  private val idsToProject = ConcurrentHashMap<ProjectId, Project>()

  /**
   * Additional ids resolving to a project via [findProject], without changing the project's canonical id.
   *
   * Needed by Remote Development: when the peers reconcile a project's identity (one side adopts the
   * other's id), an id captured in a long-running closure before the reconciliation would otherwise
   * resolve to nothing.
   */
  private val aliasesToProject = ConcurrentHashMap<ProjectId, Project>()

  init {
    cs.awaitCancellationAndInvoke {
      idsToProject.clear()
      aliasesToProject.clear()
    }
  }

  fun registerProject(project: Project): ProjectId {
    val projectId = ProjectId.create()
    project.putUserData(PROJECT_ID, projectId)
    idsToProject[projectId] = project

    return projectId
  }

  fun unregisterProject(project: Project) {
    val currentId = project.getUserData(PROJECT_ID)
    if (currentId != null) {
      idsToProject.remove(currentId)
    }
    aliasesToProject.entries.removeIf { it.value == project }
  }

  fun registerAlias(project: Project, aliasId: ProjectId) {
    aliasesToProject[aliasId] = project
  }

  fun getProjectId(project: Project): ProjectId? {
    return project.getUserData(PROJECT_ID)
  }

  fun findProject(projectId: ProjectId): Project? {
    return idsToProject[projectId] ?: aliasesToProject[projectId]
  }

  /**
   * This function is needed for remote dev, so client may set backend's project id to the local one.
   */
  fun setProjectId(project: Project, newProjectId: ProjectId) {
    val oldId = project.getUserData(PROJECT_ID)
    idsToProject.remove(oldId)

    project.putUserData(PROJECT_ID, newProjectId)
    idsToProject[newProjectId] = project
  }

  companion object {
    @ApiStatus.Internal
    private val PROJECT_ID: Key<ProjectId> = Key.create<ProjectId>("ProjectImpl.PROJECT_ID")

    @JvmStatic
    fun getInstance(): ProjectIdsStorage = service<ProjectIdsStorage>()
  }
}