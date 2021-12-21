// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package training.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import training.FeaturesTrainerIcons
import training.learn.CourseManager
import training.learn.lesson.LessonManager
import training.statistic.LessonStartingWay
import training.statistic.StatisticBase
import training.ui.LearningUiManager

private class RestartLessonAction : AnAction(FeaturesTrainerIcons.Img.ResetLesson) {
  override fun actionPerformed(e: AnActionEvent) {
    val activeToolWindow = LearningUiManager.activeToolWindow ?: return
    val lesson = LessonManager.instance.currentLesson ?: return
    StatisticBase.logLessonStopped(StatisticBase.LessonStopReason.RESTART)
    LessonManager.instance.stopLesson()
    lesson.module.primaryLanguage?.let { it.onboardingFeedbackData = null }
    CourseManager.instance.openLesson(activeToolWindow.project, lesson, LessonStartingWay.RESTART_BUTTON)
  }

  override fun update(e: AnActionEvent) {
    val activeToolWindow = LearningUiManager.activeToolWindow
    e.presentation.isEnabled = activeToolWindow != null && activeToolWindow.project == e.project
  }
}
