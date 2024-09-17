// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.jps.serialization.impl

import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.storage.url.VirtualFileUrlManager
import org.jdom.Element
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
                    componentTag: Element,
                    errorReporter: ErrorReporter,
                    virtualFileManager: VirtualFileUrlManager)

  fun saveComponent(moduleEntity: ModuleEntity): Element?

  val componentName: String
}