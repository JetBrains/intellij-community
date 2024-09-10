// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.project

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.runBlockingMaybeCancellable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.platform.kernel.withKernel
import com.jetbrains.rhizomedb.entity
import fleet.kernel.change
import fleet.kernel.shared
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
  suspend fun createEntity(project: Project): Unit = withKernel {
    val projectId = project.projectId()
    LOG.info("Creating entity for project $projectId")

    change {
      val projectEntity = shared {
        /*
        This check is added to ensure that only one ProjectEntity is going to be created in split mode.
        Two entities are possible due to a different flow in creating a project in split mode.

        First, a project is created on the backend (ProjectEntity is created at the same time).
        Then a signal about project creation is sent to the frontend via RD protocol.
        At the same time, the shared part of Rhizome DB (where ProjectEntity is stored) sends the changes to the frontend.

        Events which are coming via RD protocol are not synced with events coming via Rhizome DB.
        So it can happen that while on the backend the signal is sent strictly after ProjectEntity creation,
        on the frontend the signal can be received before there is ProjectEntity available in DB.

        If it happens that the entity has not been found and the frontend creates a new one, Rhizome DB will perform a "rebase"
        which basically re-invokes the whole "change" block either on the backend or the frontend side.
        */
        val existing = entity<ProjectEntity, ProjectId>(ProjectEntity.ProjectIdValue, projectId)
        if (existing != null) {
          existing
        }
        else {
          ProjectEntity.new {
            it[ProjectEntity.ProjectIdValue] = projectId
          }
        }
      }

      LocalProjectEntity.new {
        it[LocalProjectEntity.ProjectEntityValue] = projectEntity
        it[LocalProjectEntity.ProjectValue] = project
      }
    }

    LOG.info("Entity for project $projectId created successfully")

    Disposer.register(project, Disposable {
      LOG.info("Project $projectId is disposed, removing entity")
      runBlockingMaybeCancellable {
        removeProjectEntity(project)
      }
    })
  }

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