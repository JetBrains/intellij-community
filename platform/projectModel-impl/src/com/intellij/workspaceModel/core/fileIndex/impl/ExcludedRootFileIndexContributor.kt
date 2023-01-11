// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.core.fileIndex.impl

import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileIndexContributor
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileKind
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileSetRegistrar
import com.intellij.workspaceModel.ide.impl.virtualFile
import com.intellij.workspaceModel.storage.EntityStorage
import com.intellij.workspaceModel.storage.bridgeEntities.ExcludeUrlEntity
import com.intellij.workspaceModel.storage.bridgeEntities.library

class ExcludedRootFileIndexContributor : WorkspaceFileIndexContributor<ExcludeUrlEntity> {
  override val entityClass: Class<ExcludeUrlEntity>
    get() = ExcludeUrlEntity::class.java

  override fun registerFileSets(entity: ExcludeUrlEntity, registrar: WorkspaceFileSetRegistrar, storage: EntityStorage) {
    val file = entity.url.virtualFile
    if (file != null) {
      val kind = if (entity.library != null) WorkspaceFileKind.EXTERNAL else WorkspaceFileKind.CONTENT
      registrar.registerExcludedRoot(file, kind, entity)
    }
  }
}
