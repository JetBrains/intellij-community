// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.ide.impl.jps.serialization

import com.intellij.workspaceModel.storage.bridgeEntities.ModuleEntity
import com.intellij.workspaceModel.storage.url.VirtualFileUrl
import com.intellij.workspaceModel.storage.url.VirtualFileUrlManager
import org.jetbrains.annotations.ApiStatus

/**
 * This extension supports loading and saving additional settings from *.iml files to workspace model.
 * Implementations must be registered in the plugin.xml file:
 * ```xml
 * <extensions defaultExtensionNs="com.intellij">
 *   <workspaceModel.customModuleComponentSerializer implementation="qualified-class-name"/>
 * </extensions>
 * ```
 */
@ApiStatus.Internal
interface CustomModuleComponentSerializer {
  /**
   * [detachedModuleEntity] - module entity that is not added to the builder. You can change it by casting to builder and modify properties
   */
  fun loadComponent(detachedModuleEntity: ModuleEntity.Builder,
                    reader: JpsFileContentReader,
                    imlFileUrl: VirtualFileUrl,
                    errorReporter: ErrorReporter,
                    virtualFileManager: VirtualFileUrlManager)

  fun saveComponent(moduleEntity: ModuleEntity, imlFileUrl: VirtualFileUrl, writer: JpsFileContentWriter)
}