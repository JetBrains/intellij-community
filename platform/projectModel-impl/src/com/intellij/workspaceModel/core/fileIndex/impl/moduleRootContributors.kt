// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.core.fileIndex.impl

import com.intellij.openapi.module.Module
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.workspaceModel.core.fileIndex.*
import com.intellij.workspaceModel.ide.impl.legacyBridge.module.findModule
import com.intellij.workspaceModel.ide.impl.legacyBridge.module.roots.SourceRootTypeRegistry
import com.intellij.workspaceModel.ide.impl.virtualFile
import com.intellij.workspaceModel.storage.EntityStorage
import com.intellij.workspaceModel.storage.bridgeEntities.ContentRootEntity
import com.intellij.workspaceModel.storage.bridgeEntities.SourceRootEntity

class ContentRootFileIndexContributor : WorkspaceFileIndexContributor<ContentRootEntity> {
  override val entityClass: Class<ContentRootEntity>
    get() = ContentRootEntity::class.java

  override fun registerFileSets(entity: ContentRootEntity, registrar: WorkspaceFileSetRegistrar, storage: EntityStorage) {
    val module = entity.module.findModule(storage)
    if (module != null) {
      registrar.registerFileSet(entity.url, WorkspaceFileKind.CONTENT, entity, ModuleContentRootData(module, null))
      registrar.registerExclusionPatterns(entity.url, entity.excludedPatterns, entity)
    }
  }
}

class SourceRootFileIndexContributor : WorkspaceFileIndexContributor<SourceRootEntity> {
  override val entityClass: Class<SourceRootEntity>
    get() = SourceRootEntity::class.java

  override fun registerFileSets(entity: SourceRootEntity, registrar: WorkspaceFileSetRegistrar, storage: EntityStorage) {
    val module = entity.contentRoot.module.findModule(storage)
    if (module != null) {
      val contentRoot = entity.contentRoot.url.virtualFile
      val kind = if (SourceRootTypeRegistry.getInstance().findTypeById(entity.rootType)?.isForTests == true) WorkspaceFileKind.TEST_CONTENT else WorkspaceFileKind.CONTENT 
      registrar.registerFileSet(entity.url, kind, entity, ModuleSourceRootData(module, contentRoot, entity.rootType))
      registrar.registerExclusionPatterns(entity.url, entity.contentRoot.excludedPatterns, entity)
    }
  }

  override val dependenciesOnOtherEntities: List<DependencyDescription<SourceRootEntity>>
    get() = listOf(DependencyDescription.OnParent(ContentRootEntity::class.java) { it.sourceRoots.asSequence() })
}

/**
 * Implement this interface in custom data stored in [WorkspaceFileSet] to associate it with [Module] instance and specify 'content root' for it.
 * This information will be used by [com.intellij.openapi.roots.ProjectFileIndex.getModuleForFile]
 * and [com.intellij.openapi.roots.ProjectFileIndex.getContentRootForFile] methods.
 */
internal interface ModuleContentOrSourceRootData: WorkspaceFileSetData {
  val module: Module
  val customContentRoot: VirtualFile?
}

/**
 * Implement this interface in custom data stored in [WorkspaceFileSet] to mark it as a 'source root'. 
 * This information will be use by [com.intellij.openapi.roots.ProjectFileIndex.isInSource] and 
 * [com.intellij.openapi.roots.ProjectFileIndex.getSourceRootForFile] methods. 
 */
internal interface ModuleOrLibrarySourceRootData: WorkspaceFileSetData

internal data class ModuleContentRootData(override val module: Module, override val customContentRoot: VirtualFile?): ModuleContentOrSourceRootData

internal data class ModuleSourceRootData(override val module: Module, override val customContentRoot: VirtualFile?, val rootType: String): ModuleContentOrSourceRootData, ModuleOrLibrarySourceRootData
