// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package training.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import training.learn.lesson.LessonStateManager
import training.util.LearningLessonsAutoExecutor

private class AutorunAllLessons : AnAction() {
  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    LessonStateManager.resetPassedStatus()
    LearningLessonsAutoExecutor.runAllLessons(project)
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabledAndVisible = e.project != null
  }
}