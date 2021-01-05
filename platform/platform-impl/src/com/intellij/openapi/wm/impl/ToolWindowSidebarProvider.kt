// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl

import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.project.Project

open class ToolWindowSidebarProvider {
  open fun defaultToolwindows(project: Project): List<String> = listOf("Project", "Problems View", "Version Control", "Terminal", "Learn")

  companion object {
    fun getInstance(): ToolWindowSidebarProvider = ServiceManager.getService(ToolWindowSidebarProvider::class.java)
  }
}