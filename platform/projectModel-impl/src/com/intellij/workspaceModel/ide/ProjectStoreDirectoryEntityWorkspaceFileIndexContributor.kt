// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide

import com.intellij.platform.workspace.jps.entities.ProjectStoreDirectoryEntity
import com.intellij.platform.workspace.storage.EntityStorage
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileIndexContributor
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileKind
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileSetRegistrar

internal class ProjectStoreDirectoryEntityWorkspaceFileIndexContributor : WorkspaceFileIndexContributor<ProjectStoreDirectoryEntity> {
  override val entityClass: Class<ProjectStoreDirectoryEntity>
    get() = ProjectStoreDirectoryEntity::class.java

  override fun registerFileSets(entity: ProjectStoreDirectoryEntity, registrar: WorkspaceFileSetRegistrar, storage: EntityStorage) {
    registrar.registerFileSet(entity.url, WorkspaceFileKind.CUSTOM, entity, null)
  }
}
