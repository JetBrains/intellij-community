// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.recentFiles.backend

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project

@Service(Service.Level.PROJECT)
internal class BackendRecentFilesModel(project: Project) {
  private val state = BackendRecentFilesMutableState(project)

  companion object {
    @JvmStatic
    fun getInstance(project: Project): BackendRecentFilesModel {
      return project.getService(BackendRecentFilesModel::class.java)
    }
  }
}