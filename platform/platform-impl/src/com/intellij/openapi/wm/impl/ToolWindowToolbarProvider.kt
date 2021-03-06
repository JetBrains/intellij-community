// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl

import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowAnchor
import com.intellij.openapi.wm.ToolWindowAnchor.*

open class ToolWindowToolbarProvider {
  /**
   * Define toolwindow ids that are shown by default in left top corner (LEFT), left bottom corner (BOTTOM), right top corner (RIGHT).
   *
   * Please ensure that registered toolwindow anchor matches [anchor].
   */
  open fun defaultBottomToolwindows(project: Project, anchor: ToolWindowAnchor) =
    when (anchor) {
      LEFT -> listOf("Project", "Commit", "Structure")
      BOTTOM -> listOf("Event Log", "Problems View", "Terminal")
      RIGHT -> listOf("Database", "Gradle", "Maven")
      else -> emptyList()
    }

  companion object {
    @JvmStatic
    fun getInstance(): ToolWindowToolbarProvider = ServiceManager.getService(ToolWindowToolbarProvider::class.java)
  }
}