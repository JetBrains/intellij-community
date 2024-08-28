// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.project

import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.util.Key
import fleet.util.UID
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus

@JvmField
@ApiStatus.Internal
val PROJECT_ID = Key.create<ProjectId>("ProjectImpl.PROJECT_ID")

/**
 * Represents a unique identifier for a [Project].
 * This [ProjectId] is shared by frontend and backend.
 *
 * To retrieve the id of a given project use [projectIdOrNull] or [projectId]
 */
@Serializable
@ApiStatus.Internal
class ProjectId(private val id: UID) {

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
    @ApiStatus.Internal
    fun create(): ProjectId {
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
 * Provides the [ProjectId] for the given [Project].
 * This [ProjectId] can be used for RPC calls between frontend and backend
 *
 * @return The [ProjectId] instance associated with the provided [Project],
 * or null if [Project]'s implementation didn't assign id to it.
 */
@ApiStatus.Internal
fun Project.projectIdOrNull(): ProjectId? {
  return getUserData(PROJECT_ID)
}

/**
 * Provides the [ProjectId] for the given [Project].
 * This [ProjectId] can be used for RPC calls between frontend and backend
 *
 * @return The [ProjectId] instance associated with the provided [Project],
 * @throws IllegalStateException if [Project]'s implementation didn't assign id to it.
 */
@ApiStatus.Internal
fun Project.projectId(): ProjectId {
  return projectIdOrNull() ?: error("Project ID is not set for $this")
}

/**
 * Provides [Project] for the given [ProjectId].
 *
 * @return The [Project] instance associated with the provided [ProjectId],
 * or null if there is no project with the given [ProjectId].
 */
@ApiStatus.Internal
fun ProjectId.findProjectOrNull(): Project? {
  return ProjectManager.getInstance().openProjects.firstOrNull { it.getUserData(PROJECT_ID) == this }
}

/**
 * Provides [Project] for the given [ProjectId].
 *
 * @return The [Project] instance associated with the provided [ProjectId],
 * @throws IllegalStateException if there is no project with the given [ProjectId].
 */
@ApiStatus.Internal
fun ProjectId.findProject(): Project {
  return findProjectOrNull() ?: error("Project is not found for $this")
}
