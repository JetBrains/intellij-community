// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide.impl.legacyBridge.module.roots

import com.intellij.platform.workspace.jps.entities.SourceRootTypeId
import com.intellij.workspaceModel.ide.legacyBridge.sdk.SourceRootTypeRegistry
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.jps.model.module.JpsModuleSourceRootType
import org.jetbrains.jps.model.serialization.JpsModelSerializerExtension

@ApiStatus.Internal
class SourceRootTypeRegistryImpl : SourceRootTypeRegistry {
  @Volatile
  private var typeById: Map<String, JpsModuleSourceRootType<*>>? = null
  
  companion object {
    @JvmStatic
    fun getInstance(): SourceRootTypeRegistryImpl = SourceRootTypeRegistry.getInstance() as SourceRootTypeRegistryImpl
  }

  override fun findTypeById(rootTypeId: SourceRootTypeId): JpsModuleSourceRootType<*>? {
    return getMap()[rootTypeId.name]
  }

  private fun getMap(): Map<String, JpsModuleSourceRootType<*>> {
    val map = typeById
    if (map != null) return map
    
    val newMap = JpsModelSerializerExtension.getExtensions().flatMap { it.moduleSourceRootPropertiesSerializers }.associate { it.typeId to it.type }
    typeById = newMap
    return newMap
  }

  @ApiStatus.Internal
  fun clearCache() {
    typeById = null
  }
}