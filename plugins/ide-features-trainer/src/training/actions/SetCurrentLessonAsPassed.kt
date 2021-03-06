// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package training.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import training.learn.lesson.LessonManager

class SetCurrentLessonAsPassed: AnAction() {
  override fun actionPerformed(e: AnActionEvent) {
    val currentLesson = LessonManager.instance.currentLesson ?: return
    LessonManager.instance.passLesson(currentLesson)
  }

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabled = LessonManager.instance.currentLesson != null
  }
}