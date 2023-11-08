// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.jps.serialization.impl

import com.intellij.platform.workspace.jps.JpsFileEntitySource
import com.intellij.platform.workspace.jps.entities.LibraryEntity
import com.intellij.platform.workspace.jps.entities.LibraryId
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import com.intellij.platform.workspace.storage.url.VirtualFileUrlManager
import org.jetbrains.annotations.ApiStatus

/**
 * This extension supports loading and saving module's dependencies in custom format instead of the standard NewModuleRootManager component
 * in *.iml file. 
 * Implementations must be registered in the plugin.xml file:
 * ```xml
 * <extensions defaultExtensionNs="com.intellij">
 *   <workspaceModel.customModuleRootsSerializer implementation="qualified-class-name"/>
 * </extensions>
 * ```
 */
@ApiStatus.Internal
interface CustomModuleRootsSerializer {
  /**
   * Corresponds to value of 'classpath' module option
   */
  val id: String

  fun createEntitySource(imlFileUrl: VirtualFileUrl,
                         internalEntitySource: JpsFileEntitySource,
                         customDir: String?,
                         virtualFileManager: VirtualFileUrlManager): EntitySource?

  fun loadRoots(moduleEntity: ModuleEntity.Builder,
                reader: JpsFileContentReader,
                customDir: String?,
                imlFileUrl: VirtualFileUrl,
                internalModuleListSerializer: JpsModuleListSerializer?,
                errorReporter: ErrorReporter,
                virtualFileManager: VirtualFileUrlManager,
                moduleLibrariesCollector: MutableMap<LibraryId, LibraryEntity>)

  fun saveRoots(module: ModuleEntity,
                entities: Map<Class<out WorkspaceEntity>, List<WorkspaceEntity>>,
                writer: JpsFileContentWriter,
                customDir: String?,
                imlFileUrl: VirtualFileUrl,
                storage: EntityStorage,
                virtualFileManager: VirtualFileUrlManager)
}