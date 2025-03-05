// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide.impl

import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.impl.ProjectServiceInitializer
import com.intellij.platform.diagnostic.telemetry.impl.span
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileIndex
import com.intellij.workspaceModel.core.fileIndex.impl.WorkspaceFileIndexEx

internal class WorkspaceFileIndexInitializer : ProjectServiceInitializer {
  override suspend fun execute(project: Project) {
    span("workspace file index initialization") {
      try {
        (project.serviceAsync<WorkspaceFileIndex>() as WorkspaceFileIndexEx).initialize()
      }
      catch (e: RuntimeException) {
        // IDEA-345082 There is a chance that the index was not initialized due to the broken cache.
        WorkspaceModelCacheImpl.invalidateCaches()
        throw RuntimeException(e)
      }
    }
  }
}