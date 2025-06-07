// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.project

import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.platform.kernel.withKernel
import com.jetbrains.rhizomedb.*
import fleet.kernel.DurableEntityType
import fleet.kernel.rete.each
import fleet.kernel.rete.filter
import fleet.kernel.rete.first
import org.jetbrains.annotations.ApiStatus

/**
 * Represents a project entity that can be shared between backend and frontends.
 * The entity is created on project initialization before any services and components are loaded.
 *
 * To convert a project to the entity use [asEntityOrNull] or [asEntity]
 */
@ApiStatus.Internal
data class ProjectEntity(override val eid: EID) : Entity {
  /**
   * Represents a unique identifier for a [Project].
   * This [ProjectId] can be shared between frontend and backend.
   */
  val projectId: ProjectId by ProjectIdValue

  @ApiStatus.Internal
  companion object : DurableEntityType<ProjectEntity>(ProjectEntity::class.java.name, "com.intellij", ::ProjectEntity) {
    val ProjectIdValue: Attributes<ProjectEntity>.Required<ProjectId> = requiredValue("projectId", ProjectId.serializer(), Indexing.UNIQUE)
  }
}

/**
 * Converts a given project to its corresponding [ProjectEntity].
 *
 * The method has to be called in a kernel context - see [com.intellij.platform.kernel.withKernel]
 *
 * @return The [ProjectEntity] instance associated with the provided project,
 *         or null if no such entity is found
 */
@ApiStatus.Internal
fun Project.asEntityOrNull(): ProjectEntity? {
  return entities(ProjectEntity.ProjectIdValue, projectId()).singleOrNull()
}

/**
 * Converts a given project to its corresponding [ProjectEntity].
 *
 * The method waits until [ProjectEntity] for the given [Project] is visible in the current coroutine scope.
 *
 * For non-suspend synchronous function see [asEntityOrNull].
 *
 * @return The [ProjectEntity] instance associated with the provided project,
 */
@ApiStatus.Internal
suspend fun Project.asEntity(): ProjectEntity {
  return withKernel {
    ProjectEntity.each().filter { it.projectId == projectId() }.first()
  }
}

/**
 * Converts a given project entity to its corresponding [Project].
 *
 * The method has to be called in a kernel context - see [com.intellij.platform.kernel.withKernel]
 *
 * @return The [Project] instance associated with the provided entity,
 *         or null if no such project is found (for example, if [ProjectEntity] doesn't exist anymore).
 */
@ApiStatus.Internal
fun ProjectEntity.asProjectOrNull(): Project? {
  return ProjectManager.getInstance().openProjects.firstOrNull { it.projectId() == projectId }
}

/**
 * Converts a given project entity to its corresponding [Project].
 *
 * The method has to be called in a kernel context - see [com.intellij.platform.kernel.withKernel]
 *
 * @return The [Project] instance associated with the provided entity,
 * @throws IllegalStateException if no such project is found (for example, if [ProjectEntity] doesn't exist anymore).
 */
@ApiStatus.Internal
fun ProjectEntity.asProject(): Project {
  return asProjectOrNull() ?: error("Project is not found for $this")
}

