// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowAnchor
import com.intellij.openapi.wm.ToolWindowAnchor.*

open class ToolWindowToolbarProvider {
  /**
   * Define toolwindow ids that are shown by default in left top corner (LEFT), left bottom corner (BOTTOM), right top corner (RIGHT).
   *
   * Please ensure that registered toolwindow anchor matches [anchor].
   */
  open fun defaultToolWindows(project: Project, anchor: ToolWindowAnchor): List<String> {
    return when (anchor) {
      LEFT -> listOf("Project", "Commit", "Structure")
      BOTTOM -> listOf("Version Control", "Problems View", "Terminal", "Event Log")
      RIGHT -> listOf("Database", "Gradle", "Maven")
      else -> emptyList()
    }
  }

  companion object {
    @JvmStatic
    fun getInstance(): ToolWindowToolbarProvider = ApplicationManager.getApplication().getService(ToolWindowToolbarProvider::class.java)
  }
}