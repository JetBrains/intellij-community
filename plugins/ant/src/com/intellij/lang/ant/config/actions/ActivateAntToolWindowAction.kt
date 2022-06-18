// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.ant.config.actions

import com.intellij.icons.AllIcons
import com.intellij.ide.actions.ActivateToolWindowAction
import com.intellij.lang.ant.config.impl.AntToolWindowFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.*

class ActivateAntToolWindowAction : ActivateToolWindowAction(ToolWindowId.ANT_BUILD) {
  init {
    templatePresentation.icon = AllIcons.Toolwindows.ToolWindowAnt
  }

  override fun hasEmptyState(project: Project): Boolean {
    return true
  }

  override fun createEmptyState(project: Project) {
    val toolWindow = createToolWindow(project)
    toolWindow.show()
  }

  companion object {
    @JvmStatic
    fun createToolWindow(project: Project): ToolWindow {
      val toolWindow = ToolWindowManager.getInstance(project).registerToolWindow(
        RegisterToolWindowTask(
          ToolWindowId.ANT_BUILD,
          ToolWindowAnchor.RIGHT,
          icon = AllIcons.Toolwindows.ToolWindowAnt)
      )
      AntToolWindowFactory.registerAntExplorer(project, toolWindow)
      return toolWindow
    }
  }
}
