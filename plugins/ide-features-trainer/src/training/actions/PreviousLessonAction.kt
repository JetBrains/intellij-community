// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package training.actions

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import training.learn.CourseManager
import training.ui.LearnToolWindowFactory
import training.util.getPreviousLessonForCurrent
import training.util.lessonOpenedInProject

class PreviousLessonAction : AnAction(AllIcons.Actions.Back) {
  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    if (LearnToolWindowFactory.learnWindowPerProject[project] == null) return
    val previousLesson = getPreviousLessonForCurrent()
    CourseManager.instance.openLesson(project, previousLesson)
  }

  override fun update(e: AnActionEvent) {
    val project = e.project
    val lesson = lessonOpenedInProject(project)
    e.presentation.isEnabled = lesson != null && CourseManager.instance.lessonsForModules.firstOrNull() != lesson
  }
}
