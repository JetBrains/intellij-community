// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide

import com.intellij.platform.workspace.jps.entities.ModuleImlFileEntity
import com.intellij.platform.workspace.storage.EntityStorage
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileIndexContributor
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileKind
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileSetRegistrar

internal class ModuleImlFileEntityWorkspaceFileIndexContributor : WorkspaceFileIndexContributor<ModuleImlFileEntity> {
  override val entityClass: Class<ModuleImlFileEntity>
    get() = ModuleImlFileEntity::class.java

  override fun registerFileSets(entity: ModuleImlFileEntity, registrar: WorkspaceFileSetRegistrar, storage: EntityStorage) {
    registrar.registerNonRecursiveFileSet(entity.url, WorkspaceFileKind.CUSTOM, entity, null)
  }
}
