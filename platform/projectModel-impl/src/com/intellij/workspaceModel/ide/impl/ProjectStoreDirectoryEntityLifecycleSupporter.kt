// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide.impl

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.platform.backend.workspace.WorkspaceEntityLifecycleSupporter
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.platform.workspace.jps.entities.ProjectStoreDirectoryEntity
import com.intellij.platform.workspace.jps.entities.ProjectStoreDirectoryEntityBuilder
import com.intellij.project.ProjectStoreOwner
import com.intellij.workspaceModel.ide.NonPersistentEntitySource

/**
 * Keeps one [ProjectStoreDirectoryEntity] that points to the store directory of a directory-based project.
 */
internal class ProjectStoreDirectoryEntityLifecycleSupporter :
  WorkspaceEntityLifecycleSupporter<ProjectStoreDirectoryEntity, ProjectStoreDirectoryEntityBuilder> {
  override fun getEntityClass(): Class<ProjectStoreDirectoryEntity> = ProjectStoreDirectoryEntity::class.java

  override fun createSampleEntity(project: Project): ProjectStoreDirectoryEntityBuilder? {
    val store = (project as? ProjectStoreOwner)?.componentStore ?: return null
    val path = store.directoryStorePath ?: return null
    val url = WorkspaceModel.getInstance(project).getVirtualFileUrlManager()
      .storeAndGet(VfsUtilCore.pathToUrl(path.toAbsolutePath().toString()))
    return ProjectStoreDirectoryEntity(url, NonPersistentEntitySource)
  }

  override fun haveEqualData(first: ProjectStoreDirectoryEntityBuilder, second: ProjectStoreDirectoryEntity): Boolean {
    return first.url.getUrl() == second.url.getUrl()
  }
}
