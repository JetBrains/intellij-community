// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide.impl.jps.serialization

import com.intellij.openapi.project.ExternalStorageConfigurationManager
import com.intellij.platform.workspaceModel.jps.serialization.SerializationContext
import com.intellij.platform.workspaceModel.jps.serialization.impl.FileInDirectorySourceNames
import com.intellij.workspaceModel.storage.url.VirtualFileUrlManager

class IdeSerializationContext(
  override val virtualFileUrlManager: VirtualFileUrlManager,
  override val fileContentReader: JpsFileContentReader,
  override val fileInDirectorySourceNames: FileInDirectorySourceNames,
  private val externalStorageConfigurationManager: ExternalStorageConfigurationManager
) : SerializationContext {
  override val isExternalStorageEnabled: Boolean
    get() = externalStorageConfigurationManager.isEnabled
}