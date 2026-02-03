// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.jps.bridge

import com.intellij.platform.workspace.jps.entities.ModuleEntity
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.ApiStatus.Experimental
import org.jetbrains.jps.model.module.JpsModule

/**
 * Implement this class and register the implementation in META-INF/services/com.intellij.platform.workspace.jps.bridge.JpsModuleExtensionBridge
 * file to load custom extensions to JpsModule when JpsModel is loaded from the workspace model (IJPL-409).
 * 
 * This is needed to ensure that entities implementing [com.intellij.platform.workspace.jps.entities.ModuleSettingsFacetBridgeEntity]
 * will be loaded in JPS build process if it takes data from the workspace model. When the JPS build process fully migrates to the workspace 
 * model, entities from the workspace model may be used directly and this API won't be needed anymore.
 */
@Experimental
@ApiStatus.Internal
interface JpsModuleExtensionBridge {
  /**
   * Called during creation of [jpsModule] from [moduleEntity]. The implementation may register child elements for [jpsModule] which can
   * are used by other parts of the build process.
   */
  fun loadModuleExtensions(moduleEntity: ModuleEntity, jpsModule: JpsModule)
}
