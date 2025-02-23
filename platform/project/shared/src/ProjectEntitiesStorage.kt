// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.project

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.platform.kernel.withKernel
import com.intellij.util.AwaitCancellationAndInvoke
import com.intellij.util.awaitCancellationAndInvoke
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking
import org.jetbrains.annotations.ApiStatus

/**
 * Provides the ability to control how projects are stored in Rhizome DB
 */
@ApiStatus.Internal
abstract class ProjectEntitiesStorage {

  /**
   * Creates an entity associated with the given project.
   * This involves registering and managing project entities in the shared Rhizome DB,
   * ensuring that only one project entity is created, and handling project disposal.
   *
   * @param project The project for which the entity is to be created.
   */
  // withKernel should be kept here, since Kernel is not properly propagated in tests
  // and project entity may be created from threads without attached Kernel
  @OptIn(AwaitCancellationAndInvoke::class)
  @Suppress("DEPRECATION")
  suspend fun createEntity(project: Project): Unit = withKernel {
    val projectId = project.projectId()
    LOG.info("Creating entity for project $projectId")

    createEntityImpl(project)

    LOG.info("Entity for project $projectId created successfully")

    project.service<ProjectEntityScopeService>().coroutineScope.awaitCancellationAndInvoke {
      LOG.info("Project $projectId is disposed, removing entity")
      removeProjectEntity(project)
    }
  }

  @Service(Service.Level.PROJECT)
  private class ProjectEntityScopeService(val coroutineScope: CoroutineScope)

  /**
   * Creates [ProjectEntity] for the given [Project] and stores it in the Rhizome DB.
   *
   * @param project The project for which the entity is to be created.
   */
  protected abstract suspend fun createEntityImpl(project: Project)

  /**
   * Removes an existing entity associated with the given project.
   *
   * @param project The project whose entity is to be removed.
   */
  protected abstract suspend fun removeProjectEntity(project: Project)

  companion object {
    private val LOG = logger<ProjectEntitiesStorage>()

    fun getInstance(): ProjectEntitiesStorage = service()
  }
}