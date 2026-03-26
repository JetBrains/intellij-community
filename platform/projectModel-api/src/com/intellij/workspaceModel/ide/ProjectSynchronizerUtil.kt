// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide

import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface ProjectSynchronizerUtil {
  @ApiStatus.Internal
  suspend fun applyJpsModelToProjectModel()

  companion object {
    @JvmStatic
    suspend fun getInstance(project: Project): ProjectSynchronizerUtil = project.serviceAsync()
  }
}
