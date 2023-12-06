// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide.impl.jps.serialization

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.project.ExternalStorageConfigurationManager
import com.intellij.platform.workspace.jps.entities.ModuleSettingsBase
import com.intellij.platform.workspace.jps.serialization.SerializationContext
import com.intellij.platform.workspace.jps.serialization.impl.*
import com.intellij.platform.workspace.storage.url.VirtualFileUrlManager
import com.intellij.workspaceModel.ide.EntitiesOrphanage

abstract class BaseIdeSerializationContext : SerializationContext {
  override val isJavaPluginPresent: Boolean
    get() = PluginManagerCore.getPlugin(PluginId.findId("com.intellij.java")) != null
  
  override val isOrphanageEnabled: Boolean
    get() = EntitiesOrphanage.isEnabled
  override val customModuleComponentSerializers: List<CustomModuleComponentSerializer>
    get() = CUSTOM_MODULE_COMPONENT_SERIALIZER_EP.extensionList
  override val customModuleRootsSerializers: List<CustomModuleRootsSerializer>
    get() = CUSTOM_MODULE_ROOTS_SERIALIZER_EP.extensionList
  override val customFacetRelatedEntitySerializers: List<CustomFacetRelatedEntitySerializer<ModuleSettingsBase>>
    get() = CUSTOM_FACET_RELATED_ENTITY_SERIALIZER_EP.extensionList

  companion object {
    private val CUSTOM_MODULE_COMPONENT_SERIALIZER_EP: ExtensionPointName<CustomModuleComponentSerializer> =
      ExtensionPointName.create("com.intellij.workspaceModel.customModuleComponentSerializer")
    val CUSTOM_MODULE_ROOTS_SERIALIZER_EP: ExtensionPointName<CustomModuleRootsSerializer> =
      ExtensionPointName.create("com.intellij.workspaceModel.customModuleRootsSerializer")
    val CUSTOM_FACET_RELATED_ENTITY_SERIALIZER_EP: ExtensionPointName<CustomFacetRelatedEntitySerializer<ModuleSettingsBase>> =
      ExtensionPointName.create("com.intellij.workspaceModel.customFacetRelatedEntitySerializer")

  }
}

open class IdeSerializationContext(
  override val virtualFileUrlManager: VirtualFileUrlManager,
  override val fileContentReader: JpsFileContentReader,
  override val fileInDirectorySourceNames: FileInDirectorySourceNames,
  private val externalStorageConfigurationManager: ExternalStorageConfigurationManager
) : BaseIdeSerializationContext() {
  override val isExternalStorageEnabled: Boolean
    get() = externalStorageConfigurationManager.isEnabled
}