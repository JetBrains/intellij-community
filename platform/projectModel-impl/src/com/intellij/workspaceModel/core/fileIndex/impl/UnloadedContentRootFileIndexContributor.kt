// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.core.fileIndex.impl

import com.intellij.platform.workspace.jps.entities.ContentRootEntity
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.storage.EntityStorage
import com.intellij.workspaceModel.core.fileIndex.*

class UnloadedContentRootFileIndexContributor : WorkspaceFileIndexContributor<ContentRootEntity>,
                                                PlatformInternalWorkspaceFileIndexContributor {
  override val entityClass: Class<ContentRootEntity>
    get() = ContentRootEntity::class.java

  override fun registerFileSets(entity: ContentRootEntity, registrar: WorkspaceFileSetRegistrar, storage: EntityStorage) {
    registrar.registerExcludedRoot(entity.url, entity)
    registrar.registerFileSet(entity.url, WorkspaceFileKind.CONTENT, entity, UnloadedModuleContentRootData(entity.module.name))
  }

  override val dependenciesOnOtherEntities: List<DependencyDescription<ContentRootEntity>>
    get() = listOf(DependencyDescription.OnParent(ModuleEntity::class.java) { it.contentRoots.asSequence() })

  override val storageKind: EntityStorageKind
    get() = EntityStorageKind.UNLOADED
}

internal class UnloadedModuleContentRootData(val moduleName: String) : WorkspaceFileSetData