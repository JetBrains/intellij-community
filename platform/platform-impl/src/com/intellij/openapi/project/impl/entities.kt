// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.project.impl

import com.intellij.openapi.observable.util.whenDisposed
import com.intellij.openapi.progress.runBlockingMaybeCancellable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.getOrCreateUserData
import com.intellij.platform.kernel.KernelService
import com.intellij.platform.kernel.util.flushLatestChange
import com.intellij.platform.kernel.withKernel
import com.jetbrains.rhizomedb.*
import fleet.kernel.DurableEntityType
import fleet.kernel.change
import fleet.kernel.kernel
import fleet.kernel.shared
import fleet.util.UID
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
val KERNEL_PROJECT_ID = Key.create<UID>("ProjectImpl.KERNEL_PROJECT_ID")

/**
 * Represents a project entity that can be shared between backend and frontends.
 * The entity is created on project initialization before any services and components are loaded.
 *
 * To convert a project to the entity use [asEntity]
 */
@ApiStatus.Internal
data class ProjectEntity(override val eid: EID) : Entity {
  var projectId: UID by ProjectId

  companion object: DurableEntityType<ProjectEntity>(ProjectEntity::class.java.name, "com.intellij", ::ProjectEntity) {
    val ProjectId = requiredValue("projectId", UID.serializer(), Indexing.UNIQUE)
  }
}

data class LocalProjectEntity(override val eid: EID) : Entity {
  val sharedEntity: ProjectEntity by ProjectEntity
  val project: Project by Project

  companion object: EntityType<LocalProjectEntity>(LocalProjectEntity::class, ::LocalProjectEntity) {
    val ProjectEntity = requiredRef<ProjectEntity>("sharedEntity", RefFlags.CASCADE_DELETE_BY)
    val Project = requiredTransient<Project>("project")
  }
}


/**
 * Converts a given project to its corresponding [ProjectEntity].
 *
 * The method has to be called in a kernel context - see [com.intellij.platform.kernel.KernelService.kernelCoroutineContext]
 *
 * @return The [ProjectEntity] instance associated with the provided project,
 *         or null if no such entity is found
 */
@ApiStatus.Internal
fun Project.asEntity(): ProjectEntity? {
  return LocalProjectEntity.all().singleOrNull { it.project == this }?.sharedEntity
}

/**
 * Converts a given project entity to its corresponding [Project].
 *
 * The method has to be called in a kernel context - see [com.intellij.platform.kernel.KernelService.kernelCoroutineContext]
 *
 * @return The [Project] instance associated with the provided entity,
 *         or null if no such project is found (for example, if [ProjectEntity] doesn't exist anymore).
 */
@ApiStatus.Internal
fun ProjectEntity.asProject(): Project? {
  return LocalProjectEntity.all().singleOrNull { it.sharedEntity == this }?.project
}

internal suspend fun Project.createEntity() = withKernel {
  val project = this@createEntity
  val projectId = project.getOrCreateUserData(KERNEL_PROJECT_ID) { UID.random() }

  // TODO it shouldn't be here
  change {
    shared {
      register(ProjectEntity)
    }
  }

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
      val existing = ProjectEntity.all().singleOrNull { it.projectId == projectId }
      if (existing != null) {
        existing
      }
      else {
        ProjectEntity.new {
          it[ProjectEntity.ProjectId] = projectId
        }
      }
    }

    LocalProjectEntity.new {
      it[LocalProjectEntity.ProjectEntity] = projectEntity
      it[LocalProjectEntity.Project] = project
    }
  }

  project.whenDisposed {
    runBlockingMaybeCancellable {
      removeProjectEntity(project)
    }
  }
}

private suspend fun removeProjectEntity(project: Project) = withKernel {
  change {
    shared {
      project.asEntity()?.delete()
    }
  }

  // Removing ProjectEntity and LocalProjectEntity is the last operation in most of the tests
  // Without calling "flushLatestChange" kernel keeps the project, which causes "testProjectLeak" failures
  kernel().flushLatestChange()
}
