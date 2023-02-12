// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide.impl.jps.serialization

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.project.ExternalStorageConfigurationManager
import com.intellij.platform.workspaceModel.jps.serialization.SerializationContext
import com.intellij.platform.workspaceModel.jps.serialization.impl.FileInDirectorySourceNames
import com.intellij.workspaceModel.ide.EntitiesOrphanage
import com.intellij.workspaceModel.storage.url.VirtualFileUrlManager

abstract class BaseIdeSerializationContext : SerializationContext {
  override val isJavaPluginPresent: Boolean
    get() = PluginManagerCore.getPlugin(PluginId.findId("com.intellij.java")) != null
  
  override val isOrphanageEnabled: Boolean
    get() = EntitiesOrphanage.isEnabled
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