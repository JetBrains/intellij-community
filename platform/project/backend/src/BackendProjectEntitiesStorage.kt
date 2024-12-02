// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.project.backend

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.platform.kernel.util.flushLatestChange
import com.intellij.platform.kernel.withKernel
import com.intellij.platform.project.ProjectEntitiesStorage
import com.intellij.platform.project.ProjectEntity
import com.intellij.platform.project.asEntityOrNull
import com.intellij.platform.project.projectId
import fleet.kernel.change
import fleet.kernel.shared
import fleet.kernel.transactor

internal class BackendProjectEntitiesStorage : ProjectEntitiesStorage() {
  override suspend fun createEntityImpl(project: Project) {
    val projectId = project.projectId()
    //change {
    //  shared {
    //    ProjectEntity.new {
    //      it[ProjectEntity.ProjectIdValue] = projectId
    //    }
    //  }
    //}

    // migrate to the implementation above when IJPL-172500 is investigated and a fix has been found
    change {
      shared {
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
        ProjectEntity.upsert(ProjectEntity.ProjectIdValue, projectId) {
          it[ProjectEntity.ProjectIdValue] = projectId
        }
      }
    }
    LOG.info("Project entity created for $projectId")
  }

  override suspend fun removeProjectEntity(project: Project): Unit = withKernel {
    change {
      shared {
        val entity = project.asEntityOrNull() ?: run {
          LOG.error("Project entity hasn't been found for $project")
          return@shared
        }
        entity.delete()
      }
    }

    // Removing ProjectEntity and LocalProjectEntity is the last operation in most of the tests
    // Without calling "flushLatestChange" kernel keeps the project, which causes "testProjectLeak" failures
    transactor().flushLatestChange()
  }

  companion object {
    private val LOG = logger<BackendProjectEntitiesStorage>()
  }
}
