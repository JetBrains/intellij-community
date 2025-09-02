// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide

import com.intellij.platform.workspace.storage.EntityStorage
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileIndexContributor
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileKind
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileSetRegistrar

internal class ProjectRootEntityWorkspaceFileIndexContributor: WorkspaceFileIndexContributor<ProjectRootEntity> {
  override val entityClass: Class<ProjectRootEntity>
    get() = ProjectRootEntity::class.java

  override fun registerFileSets(entity: ProjectRootEntity, registrar: WorkspaceFileSetRegistrar, storage: EntityStorage) {
    registrar.registerFileSet(entity.root, WorkspaceFileKind.CONTENT_NON_INDEXABLE, entity, null)
  }
}
