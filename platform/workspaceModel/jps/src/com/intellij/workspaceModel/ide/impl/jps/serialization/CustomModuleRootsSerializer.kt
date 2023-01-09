// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.ide.impl.jps.serialization

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.workspaceModel.ide.JpsFileEntitySource
import com.intellij.workspaceModel.storage.*
import com.intellij.workspaceModel.storage.bridgeEntities.ModuleEntity
import com.intellij.workspaceModel.storage.url.VirtualFileUrl
import com.intellij.workspaceModel.storage.url.VirtualFileUrlManager
import org.jetbrains.annotations.ApiStatus

/**
 * This extension supports loading and saving module's dependencies in custom format instead of the standard NewModuleRootManager component
 * in *.iml file.
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

  fun loadRoots(builder: MutableEntityStorage,
                moduleEntity: ModuleEntity,
                reader: JpsFileContentReader,
                customDir: String?,
                imlFileUrl: VirtualFileUrl,
                internalModuleListSerializer: JpsModuleListSerializer?,
                errorReporter: ErrorReporter,
                virtualFileManager: VirtualFileUrlManager)

  fun saveRoots(module: ModuleEntity,
                entities: Map<Class<out WorkspaceEntity>, List<WorkspaceEntity>>,
                writer: JpsFileContentWriter,
                customDir: String?,
                imlFileUrl: VirtualFileUrl,
                storage: EntityStorage,
                virtualFileManager: VirtualFileUrlManager)

  companion object {
    val EP_NAME: ExtensionPointName<CustomModuleRootsSerializer> = ExtensionPointName.create("com.intellij.workspaceModel.customModuleRootsSerializer")
  }
}