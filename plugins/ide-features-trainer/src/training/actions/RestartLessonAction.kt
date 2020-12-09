// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package training.actions

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import icons.FeaturesTrainerIcons
import training.learn.CourseManager
import training.learn.lesson.LessonManager
import training.ui.LearningUiManager

class RestartLessonAction : AnAction(FeaturesTrainerIcons.Img.ResetLesson) {
  override fun actionPerformed(e: AnActionEvent) {
    val activeToolWindow = LearningUiManager.activeToolWindow ?: return
    val lesson = LessonManager.instance.currentLesson ?: return
    CourseManager.instance.openLesson(activeToolWindow.project, lesson)
  }

  override fun update(e: AnActionEvent) {
    val activeToolWindow = LearningUiManager.activeToolWindow
    e.presentation.isEnabled = activeToolWindow != null && activeToolWindow.project == e.project
  }
}
