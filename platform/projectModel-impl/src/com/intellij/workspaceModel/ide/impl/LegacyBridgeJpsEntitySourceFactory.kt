// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide.impl

import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectModelExternalSource
import com.intellij.platform.workspace.jps.serialization.impl.FileInDirectorySourceNames
import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import com.intellij.workspaceModel.ide.impl.legacyBridge.LegacyBridgeJpsEntitySourceFactoryInternal
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@ApiStatus.ScheduledForRemoval
@Deprecated("Replace with com.intellij.workspaceModel.ide.legacyBridge.LegacyBridgeJpsEntitySourceFactory")
object LegacyBridgeJpsEntitySourceFactory {
  fun createEntitySourceForModule(
    project: Project,
    baseModuleDir: VirtualFileUrl,
    externalSource: ProjectModelExternalSource?,
    fileInDirectoryNames: FileInDirectorySourceNames? = null,
    moduleFileName: String? = null,
  ): EntitySource {
    val instance = com.intellij.workspaceModel.ide.legacyBridge.LegacyBridgeJpsEntitySourceFactory.getInstance(project) as LegacyBridgeJpsEntitySourceFactoryInternal
    return instance.createEntitySourceForModule(baseModuleDir, externalSource, fileInDirectoryNames, moduleFileName)
  }

  fun createEntitySourceForProjectLibrary(
    project: Project,
    externalSource: ProjectModelExternalSource?,
    fileInDirectoryNames: FileInDirectorySourceNames? = null,
    fileName: String? = null,
  ): EntitySource {
    val instance = com.intellij.workspaceModel.ide.legacyBridge.LegacyBridgeJpsEntitySourceFactory.getInstance(project) as LegacyBridgeJpsEntitySourceFactoryInternal
    return instance.createEntitySourceForProjectLibrary(externalSource, fileInDirectoryNames, fileName)
  }
}
