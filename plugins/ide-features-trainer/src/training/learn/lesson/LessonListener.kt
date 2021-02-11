// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package training.learn.lesson

import training.learn.course.Lesson
import java.util.*

interface LessonListener : EventListener {

  fun lessonStarted(lesson: Lesson) {}

  fun lessonPassed(lesson: Lesson) {}

  fun lessonStopped(lesson: Lesson) {}
}