// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.project

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import fleet.util.UID
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus

private val LOG = fileLogger()

/**
 * Represents a unique identifier for a [Project].
 * This [ProjectId] is shared by frontend and backend.
 *
 * To retrieve the id of a given project use [projectIdOrNull] or [projectId]
 */
@Serializable
@ApiStatus.Experimental
data class ProjectId(private val id: UID) {

  /**
   * This API is necessary only for Split Mode, functionality that uses RD Protocol
   * for RPC use just [ProjectId], since it is serializable
   */
  @ApiStatus.Internal
  fun serializeToString(): String {
    return id.toString()
  }

  companion object {
    /**
     * Creates a new unique identifier for a [Project]
     */
    internal fun create(): ProjectId {
      return ProjectId(UID.random())
    }

    /**
     * This API is necessary only for Split Mode, functionality that uses RD Protocol
     * for RPC use just [ProjectId], since it is serializable
     */
    @ApiStatus.Internal
    fun deserializeFromString(value: String): ProjectId {
      return ProjectId(UID.fromString(value))
    }
  }
}

/**
 * Creates and assigns a new [ProjectId] to the given [Project].
 * The assigned [ProjectId] will be used to uniquely identify this [Project] instance.
 *
 * When the [ProjectId] is assigned, future calls to [ProjectId.findProject] will return this [project].
 *
 * Has to be called by [Project] implementations ONLY.
 */
@ApiStatus.Internal
fun registerNewProjectId(project: Project): ProjectId? {
  return ProjectIdsStorage.getInstance().registerProject(project)
}

/**
 * Removes the [ProjectId] associated with the given [Project].
 * This method should be called when a project is being disposed to prevent memory leaks.
 *
 * After unregistering, the [ProjectId] will no longer be available for this [Project] instance,
 * and [ProjectId.findProject] will return [null].
 *
 * Has to be called by [Project] implementations ONLY.
 */
@ApiStatus.Internal
fun unregisterProjectId(project: Project) {
  ProjectIdsStorage.getInstance().unregisterProject(project)
}

/**
 * Sets a specific [ProjectId] for the given [Project].
 * Previously associated [ProjectId] won't be attached to the [Project] anymore.
 *
 * When the [newProjectId] is assigned, future calls to [ProjectId.findProject] will return this [project].
 *
 * Has to be called by Remote Development implementation only.
 */
@ApiStatus.Internal
fun setNewProjectId(project: Project, newProjectId: ProjectId) {
  ProjectIdsStorage.getInstance().setProjectId(project, newProjectId)
}

/**
 * Provides the [ProjectId] for the given [Project].
 * This [ProjectId] can be used for RPC calls between frontend and backend.
 *
 * **WARNING: This API is experimental and should be used with care.**
 *
 * This function retrieves the unique identifier assigned to a Project instance, returning null if not found.
 * Important considerations:
 * - Returns null for projects that have not been properly registered with a ProjectId
 * - In Remote Development scenarios, ensures project identity is maintained across frontend/backend boundaries
 * - Safe to use for RPC transmission as ProjectId is serializable
 * - Safer alternative to [projectId] that doesn't throw exceptions
 *
 * @return The [ProjectId] instance associated with the provided [Project],
 *         or null if [Project]'s implementation didn't assign id to it
 */
@ApiStatus.Experimental
fun Project.projectIdOrNull(): ProjectId? {
  return ProjectIdsStorage.getInstance().getProjectId(this)
}

/**
 * Provides the [ProjectId] for the given [Project].
 * This [ProjectId] can be used for RPC calls between frontend and backend.
 *
 * **WARNING: This API is experimental and should be used with care.**
 *
 * This function retrieves the unique identifier assigned to a Project instance. Important considerations:
 * - Only works for projects that have been properly registered with a ProjectId
 * - In Remote Development scenarios, ensures project identity is maintained across frontend/backend boundaries
 * - Safe to use for RPC transmission as ProjectId is serializable
 *
 * @return The [ProjectId] instance associated with the provided [Project]
 * @throws IllegalStateException if [Project]'s implementation didn't assign id to it
 */
@ApiStatus.Experimental
fun Project.projectId(): ProjectId {
  return projectIdOrNull() ?: error("Project ID is not set for $this")
}

@ApiStatus.Experimental
fun Project.projectIdOrNullWithLogError(log: Logger): ProjectId? {
  val projectId = projectIdOrNull()
  if (projectId == null) {
    log.error("Project ID is not set for $this")
  }
  return projectId
}

/**
 * Provides [Project] for the given [ProjectId].
 *
 * **WARNING: This API is experimental and should be used with care.**
 *
 * This function attempts to locate a Project instance by its unique identifier. Important considerations:
 * - Only finds projects that are currently open and registered in the IDE
 * - In Remote Development scenarios, only searches for projects on the current (backend) side
 * - May return null if the project was closed or unregistered after the ProjectId was obtained
 *
 * @return The [Project] instance associated with the provided [ProjectId],
 *         or null if there is no project with the given [ProjectId]
 */
@ApiStatus.Experimental
fun ProjectId.findProjectOrNull(): Project? {
  return ProjectIdsStorage.getInstance().findProject(this)
}

/**
 * Provides [Project] for the given [ProjectId].
 *
 * @return The [Project] instance associated with the provided [ProjectId],
 * or null if there is no project with the given [ProjectId] and logs an error.
 */
@ApiStatus.Experimental
fun ProjectId.findProjectOrNullWithLogError(log: Logger): Project? {
  val project = ProjectIdsStorage.getInstance().findProject(this)
  if (project == null) {
    log.error("Project is not found for $this. Opened projects: ${ProjectManager.getInstance().openProjects.joinToString { it.projectId().toString() }}")
  }
  return project
}

/**
 * Provides [Project] for the given [ProjectId].
 *
 * **WARNING: This API is experimental and should be used with care.**
 *
 * This function attempts to locate a Project instance by its unique identifier and throws an exception if not found.
 * Important considerations:
 * - Only finds projects that are currently open and registered in the IDE
 * - Throws an exception if the project was closed or unregistered after the ProjectId was obtained
 *
 * @return The [Project] instance associated with the provided [ProjectId]
 * @throws IllegalStateException if there is no project with the given [ProjectId]
 */
@ApiStatus.Experimental
fun ProjectId.findProject(): Project {
  return findProjectOrNull() ?: run {
    LOG.error("Project is not found for $this. Opened projects: ${ProjectManager.getInstance().openProjects.joinToString { it.projectId().toString() }}")
    error("Project is not found for $this")
  }
}