// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package training.statistic

import com.intellij.openapi.project.Project
import training.learn.course.Lesson
import training.learn.lesson.LessonListener

class StatisticLessonListener(val project: Project) : LessonListener {

  override fun lessonStarted(lesson: Lesson) {
    StatisticBase.logLessonStarted(lesson)
  }

  override fun lessonPassed(lesson: Lesson) {
    StatisticBase.logLessonPassed(lesson)
  }
}