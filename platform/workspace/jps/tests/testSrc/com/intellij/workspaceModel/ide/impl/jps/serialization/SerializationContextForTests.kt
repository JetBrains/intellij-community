// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide.impl.jps.serialization

import com.intellij.platform.workspace.jps.serialization.impl.FileInDirectorySourceNames
import com.intellij.platform.workspace.jps.serialization.impl.JpsFileContentReader
import com.intellij.platform.workspace.storage.url.VirtualFileUrlManager

class SerializationContextForTests(
  override val virtualFileUrlManager: VirtualFileUrlManager,
  override val fileContentReader: JpsFileContentReader,
  override val isExternalStorageEnabled: Boolean = false,
  override val fileInDirectorySourceNames: FileInDirectorySourceNames = FileInDirectorySourceNames.empty()
) : BaseIdeSerializationContext()