// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl

import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.annotations.ApiStatus.Experimental

abstract class FrameTitleBuilder {
  abstract fun getProjectTitle(project: Project): String

  abstract fun getFileTitle(project: Project, file: VirtualFile): String

  @Experimental
  open suspend fun getFileTitleAsync(project: Project, file: VirtualFile): String {
    return readAction {
      getFileTitle(project, file)
    }
  }

  companion object {
    @JvmStatic
    fun getInstance(): FrameTitleBuilder = service<FrameTitleBuilder>()
  }
}