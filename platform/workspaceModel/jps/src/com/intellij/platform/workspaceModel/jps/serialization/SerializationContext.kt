// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspaceModel.jps.serialization

import com.intellij.platform.workspaceModel.jps.serialization.impl.FileInDirectorySourceNames
import com.intellij.workspaceModel.ide.impl.jps.serialization.JpsFileContentReader
import com.intellij.workspaceModel.storage.url.VirtualFileUrlManager

/**
 * Provides components which are required to load and store projects in JPS format.
 */
interface SerializationContext {
  val virtualFileUrlManager: VirtualFileUrlManager
  val fileContentReader: JpsFileContentReader
  val isExternalStorageEnabled: Boolean
  val fileInDirectorySourceNames: FileInDirectorySourceNames
}