// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.ant.config.actions

import com.intellij.icons.AllIcons
import com.intellij.ide.actions.ActivateToolWindowAction
import com.intellij.lang.ant.config.impl.AntToolWindowFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.*
import java.util.function.Supplier

internal class ActivateAntToolWindowAction : ActivateToolWindowAction(ToolWindowId.ANT_BUILD) {
  init {
    templatePresentation.iconSupplier = Supplier { AllIcons.Toolwindows.ToolWindowAnt }
  }

  override fun hasEmptyState(project: Project) = true

  override fun createEmptyState(project: Project) {
    Manager.createToolWindow(project).show()
  }

  object Manager {
    fun createToolWindow(project: Project): ToolWindow {
      val toolWindow = ToolWindowManager.getInstance(project).registerToolWindow(
        RegisterToolWindowTask(
          id = ToolWindowId.ANT_BUILD,
          anchor = ToolWindowAnchor.RIGHT,
          icon = AllIcons.Toolwindows.ToolWindowAnt,
        )
      )
      AntToolWindowFactory.registerAntExplorer(project, toolWindow)
      return toolWindow
    }
  }
}
