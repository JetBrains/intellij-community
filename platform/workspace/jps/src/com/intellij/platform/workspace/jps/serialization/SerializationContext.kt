// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.jps.serialization

import com.intellij.platform.workspace.jps.serialization.impl.*
import com.intellij.platform.workspace.storage.url.VirtualFileUrlManager
import org.jetbrains.annotations.ApiStatus

/**
 * Provides components which are required to load and store projects in JPS format.
 */
@ApiStatus.Internal
interface SerializationContext {
  val virtualFileUrlManager: VirtualFileUrlManager
  val fileContentReader: JpsFileContentReader
  val isExternalStorageEnabled: Boolean
  val fileInDirectorySourceNames: FileInDirectorySourceNames

  /**
   * Returns `true` if Java plugin is enabled. This is a temporary solution to support serialization of Java-specific properties, which
   * currently is implemented inside the platform. 
   */
  val isJavaPluginPresent: Boolean

  val customModuleComponentSerializers: List<CustomModuleComponentSerializer>
  
  val customModuleRootsSerializers: List<CustomModuleRootsSerializer>
  
  val customFacetRelatedEntitySerializers: List<CustomFacetRelatedEntitySerializer<*>>
}