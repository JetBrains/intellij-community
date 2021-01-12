// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package training.learn.lesson.listeners

import com.intellij.openapi.project.Project
import training.learn.interfaces.Lesson
import training.learn.lesson.LessonListener
import training.statistic.StatisticBase

class StatisticLessonListener(val project: Project) : LessonListener {

  override fun lessonStarted(lesson: Lesson) {
    StatisticBase.logLessonStarted(lesson)
  }

  override fun lessonPassed(lesson: Lesson) {
    StatisticBase.logLessonPassed(lesson)
  }
}