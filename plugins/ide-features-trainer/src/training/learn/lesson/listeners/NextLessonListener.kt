// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package training.learn.lesson.listeners

import com.intellij.openapi.project.Project
import training.learn.CourseManager
import training.learn.exceptons.BadLessonException
import training.learn.exceptons.BadModuleException
import training.learn.exceptons.LessonIsOpenedException
import training.learn.interfaces.Lesson
import training.learn.lesson.LessonListener
import java.awt.FontFormatException
import java.io.IOException
import java.util.concurrent.ExecutionException

class NextLessonListener(val project: Project) : LessonListener {

  @Throws(BadLessonException::class, ExecutionException::class, IOException::class, FontFormatException::class, InterruptedException::class,
          BadModuleException::class, LessonIsOpenedException::class)
  override fun lessonNext(lesson: Lesson) {
    if (lesson.module.hasNotPassedLesson()) {
      val nextLesson = lesson.module.giveNotPassedAndNotOpenedLesson()
                       ?: throw BadLessonException("Unable to obtain not passed and not opened lessons")
      CourseManager.instance.openLesson(project, nextLesson)
    }
  }
}