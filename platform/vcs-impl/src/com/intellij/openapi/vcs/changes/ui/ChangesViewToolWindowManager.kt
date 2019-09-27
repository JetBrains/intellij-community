// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.ui

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow

interface ChangesViewToolWindowManager {
  fun setToolWindow(toolWindow: ToolWindow)

  fun shouldBeAvailable(): Boolean

  companion object {
    @JvmStatic
    fun getInstance(project: Project) = project.service<ChangesViewToolWindowManager>()
  }
}