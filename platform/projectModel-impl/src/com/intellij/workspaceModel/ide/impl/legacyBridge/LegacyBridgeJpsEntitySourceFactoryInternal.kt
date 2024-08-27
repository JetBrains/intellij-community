// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide.impl.legacyBridge

import com.intellij.openapi.roots.ProjectModelExternalSource
import com.intellij.platform.workspace.jps.serialization.impl.FileInDirectorySourceNames
import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import com.intellij.workspaceModel.ide.legacyBridge.LegacyBridgeJpsEntitySourceFactory
import org.jetbrains.annotations.ApiStatus

/**
 * Factory to generate entity sources for JPS entities.
 * Entities with these entity sources will generate related files in the .idea directory
 */
@ApiStatus.Internal
interface LegacyBridgeJpsEntitySourceFactoryInternal : LegacyBridgeJpsEntitySourceFactory {
  fun createEntitySourceForModule(
    baseModuleDir: VirtualFileUrl,
    externalSource: ProjectModelExternalSource?,
    fileInDirectoryNames: FileInDirectorySourceNames? = null,
    moduleFileName: String? = null,
  ): EntitySource


  fun createEntitySourceForProjectLibrary(
    externalSource: ProjectModelExternalSource?,
    fileInDirectoryNames: FileInDirectorySourceNames? = null,
    fileName: String? = null,
  ): EntitySource
}
