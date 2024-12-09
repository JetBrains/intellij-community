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
    change {
      shared {
        ProjectEntity.upsert(ProjectEntity.ProjectIdValue, projectId) {
          it[ProjectEntity.ProjectIdValue] = projectId
        }
      }
    }
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
