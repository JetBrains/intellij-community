// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.ide.impl.jps.serialization

import com.intellij.workspaceModel.storage.MutableEntityStorage
import com.intellij.workspaceModel.storage.bridgeEntities.ModuleEntity
import com.intellij.workspaceModel.storage.url.VirtualFileUrl
import com.intellij.workspaceModel.storage.url.VirtualFileUrlManager
import org.jetbrains.annotations.ApiStatus

/**
 * This extension supports loading and saving additional settings from *.iml files to workspace model. 
 */
@ApiStatus.Internal
interface CustomModuleComponentSerializer {
  fun loadComponent(builder: MutableEntityStorage,
                    moduleEntity: ModuleEntity,
                    reader: JpsFileContentReader,
                    imlFileUrl: VirtualFileUrl,
                    errorReporter: ErrorReporter,
                    virtualFileManager: VirtualFileUrlManager)

  fun saveComponent(moduleEntity: ModuleEntity, imlFileUrl: VirtualFileUrl, writer: JpsFileContentWriter)
}