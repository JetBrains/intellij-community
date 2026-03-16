// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package training.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import training.util.learningToolWindow

internal class OpenLearnPanel : DumbAwareAction() {
  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project
    if (project != null) {
      val toolWindow = learningToolWindow(project) ?: return
      toolWindow.show()
    }
  }

  override fun update(e: AnActionEvent) {
    val project = e.project
    e.presentation.isEnabledAndVisible = project != null && learningToolWindow(project) != null
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }
}
