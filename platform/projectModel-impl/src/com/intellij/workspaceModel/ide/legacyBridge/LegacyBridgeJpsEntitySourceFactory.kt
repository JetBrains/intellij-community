// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide.legacyBridge

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectModelExternalSource
import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.url.VirtualFileUrl

/**
 * Factory to create entity sources for JPS entities.
 * Entities with these entity sources will generate related files in the .idea directory
 */
interface LegacyBridgeJpsEntitySourceFactory {
  fun createEntitySourceForModule(
    baseModuleDir: VirtualFileUrl,
    externalSource: ProjectModelExternalSource?,
  ): EntitySource

  fun createEntitySourceForProjectLibrary(externalSource: ProjectModelExternalSource?): EntitySource

  companion object {
    fun getInstance(project: Project): LegacyBridgeJpsEntitySourceFactory = project.service()
  }
}
