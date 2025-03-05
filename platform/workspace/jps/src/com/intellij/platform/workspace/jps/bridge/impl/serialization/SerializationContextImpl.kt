// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.jps.bridge.impl.serialization

import com.intellij.platform.workspace.jps.serialization.SerializationContext
import com.intellij.platform.workspace.jps.serialization.impl.*
import com.intellij.platform.workspace.storage.url.VirtualFileUrlManager

internal class SerializationContextImpl(
  override val virtualFileUrlManager: VirtualFileUrlManager,
  override val fileContentReader: JpsFileContentReader,
) : SerializationContext {
  
  override val isExternalStorageEnabled: Boolean
    get() = false //todo
  override val fileInDirectorySourceNames: FileInDirectorySourceNames
    get() = FileInDirectorySourceNames.empty()
  
  override val isJavaPluginPresent: Boolean
    get() = true
  
  override val customModuleComponentSerializers: List<CustomModuleComponentSerializer>
    get() = emptyList() //todo
  
  override val customModuleRootsSerializers: List<CustomModuleRootsSerializer>
    get() = emptyList() //todo
  
  override val customFacetRelatedEntitySerializers: List<CustomFacetRelatedEntitySerializer<*>>
    get() = listOf(DefaultFacetEntitySerializer()) //todo
}
