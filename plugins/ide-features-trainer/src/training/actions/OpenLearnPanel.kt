// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package training.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.wm.ToolWindowManager
import training.learn.OpenLessonActivities
import training.ui.LearnToolWindowFactory

class OpenLearnPanel : DumbAwareAction() {
  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project
    if (project != null) {
      val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(LearnToolWindowFactory.LEARN_TOOL_WINDOW) ?: return
      toolWindow.show()
    }
    else {
      OpenLessonActivities.openLearnProjectFromWelcomeScreen()
    }
  }
}
