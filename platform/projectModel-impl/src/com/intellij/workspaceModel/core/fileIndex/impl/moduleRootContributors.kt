// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.core.fileIndex.impl

import com.intellij.java.workspace.entities.JavaModuleSettingsEntity
import com.intellij.java.workspace.entities.JavaResourceRootPropertiesEntity
import com.intellij.java.workspace.entities.JavaSourceRootPropertiesEntity
import com.intellij.java.workspace.entities.asJavaResourceRoot
import com.intellij.java.workspace.entities.asJavaSourceRoot
import com.intellij.openapi.module.Module
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.backend.workspace.virtualFile
import com.intellij.platform.workspace.jps.entities.ContentRootEntity
import com.intellij.platform.workspace.jps.entities.SourceRootEntity
import com.intellij.platform.workspace.jps.entities.SourceRootTypeId
import com.intellij.platform.workspace.storage.EntityStorage
import com.intellij.workspaceModel.core.fileIndex.*
import com.intellij.workspaceModel.ide.impl.legacyBridge.module.findModule
import com.intellij.workspaceModel.ide.legacyBridge.SourceRootTypeRegistry
import org.jetbrains.annotations.ApiStatus

class ContentRootFileIndexContributor : WorkspaceFileIndexContributor<ContentRootEntity>, PlatformInternalWorkspaceFileIndexContributor {
  override val entityClass: Class<ContentRootEntity>
    get() = ContentRootEntity::class.java

  override fun registerFileSets(entity: ContentRootEntity, registrar: WorkspaceFileSetRegistrar, storage: EntityStorage) {
    val module = entity.module.findModule(storage)
    if (module != null) {
      registrar.registerFileSet(entity.url, WorkspaceFileKind.CONTENT, entity, ModuleContentRootData(module, null))
      registrar.registerExclusionPatterns(entity.url, entity.excludedPatterns, entity)
    }
  }

  override val dependenciesOnOtherEntities: List<DependencyDescription<ContentRootEntity>>
    get() = listOf(
      DependencyDescription.OnArbitraryEntity(JavaModuleSettingsEntity::class.java) {
        it.module.contentRoots.asSequence()
      }
    )
}

class SourceRootFileIndexContributor : WorkspaceFileIndexContributor<SourceRootEntity>, PlatformInternalWorkspaceFileIndexContributor {
  override val entityClass: Class<SourceRootEntity>
    get() = SourceRootEntity::class.java

  override fun registerFileSets(entity: SourceRootEntity, registrar: WorkspaceFileSetRegistrar, storage: EntityStorage) {
    val module = entity.contentRoot.module.findModule(storage)
    if (module != null) {
      val contentRoot = entity.contentRoot.url.virtualFile
      val kind = if (SourceRootTypeRegistry.getInstance().findTypeById(entity.rootTypeId)?.isForTests == true) WorkspaceFileKind.TEST_CONTENT else WorkspaceFileKind.CONTENT
      val javaProperties = entity.asJavaSourceRoot()
      val resourceProperties = entity.asJavaResourceRoot()
      val packagePrefix = javaProperties?.packagePrefix
                          ?: resourceProperties?.relativeOutputPath?.replace('/', '.')
                          ?: ""
      val forGeneratedSources = javaProperties != null && javaProperties.generated || resourceProperties != null && resourceProperties.generated
      registrar.registerFileSet(entity.url, kind, entity, ModuleSourceRootData(module, contentRoot, entity.rootTypeId, packagePrefix, forGeneratedSources))
      registrar.registerExclusionPatterns(entity.url, entity.contentRoot.excludedPatterns, entity)
    }
  }

  override val dependenciesOnOtherEntities: List<DependencyDescription<SourceRootEntity>>
    get() = listOf(
      DependencyDescription.OnParent(ContentRootEntity::class.java) { it.sourceRoots.asSequence() },
      DependencyDescription.OnChild(JavaSourceRootPropertiesEntity::class.java) { it.sourceRoot },
      DependencyDescription.OnChild(JavaResourceRootPropertiesEntity::class.java) { it.sourceRoot }
    )
}

/**
 * Implement this interface in custom data stored in [WorkspaceFileSet] to associate it with [Module] instance.
 * This information will be used by [com.intellij.openapi.roots.ProjectFileIndex.getModuleForFile] method.
 */
interface ModuleRelatedRootData: WorkspaceFileSetData {
  val module: Module
}

/**
 * Implement this interface in custom data stored in [WorkspaceFileSet] to associate it with [Module] instance and specify 'content root' for it.
 * This information will be used by [com.intellij.openapi.roots.ProjectFileIndex.getModuleForFile]
 * and [com.intellij.openapi.roots.ProjectFileIndex.getContentRootForFile] methods.
 */
interface ModuleContentOrSourceRootData: ModuleRelatedRootData {
  val customContentRoot: VirtualFile?
}

/**
 * Implement this interface in custom data stored in [WorkspaceFileSet] to mark it as a 'source root'. 
 * This information will be use by [com.intellij.openapi.roots.ProjectFileIndex.isInSource] and 
 * [com.intellij.openapi.roots.ProjectFileIndex.getSourceRootForFile] methods. 
 */
interface ModuleOrLibrarySourceRootData: WorkspaceFileSetData

/**
 * Marks files sets which correspond to JVM packages. This interface will be removed from the platform when we get rid of Java-specific
 * methods like [com.intellij.openapi.roots.ProjectFileIndex.getPackageNameByDirectory] in the platform API, so plugins must use
 * [com.intellij.java.workspace.fileIndex.JvmPackageRootData] instead. 
 */
@ApiStatus.Internal
interface JvmPackageRootDataInternal: WorkspaceFileSetData {
  val packagePrefix: String
}

internal data class ModuleContentRootData(override val module: Module, override val customContentRoot: VirtualFile?): ModuleContentOrSourceRootData

internal data class ModuleSourceRootData(
  override val module: Module,
  override val customContentRoot: VirtualFile?,
  val rootTypeId: SourceRootTypeId,
  override val packagePrefix: String,
  val forGeneratedSources: Boolean
) : ModuleContentOrSourceRootData, ModuleOrLibrarySourceRootData, JvmPackageRootDataInternal
