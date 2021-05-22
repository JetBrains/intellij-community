// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package training.learn.log

import training.learn.course.Lesson
import java.util.*

class LessonLog(lesson: Lesson) {

  private val logData = mutableListOf<Pair<Date, String>>()
  private var exerciseCount = 0

  fun log(actionString: String) {
    logData.add(Pair(Date(), actionString))
  }

  fun resetCounter() {
    exerciseCount = 0
  }

  fun print() {
    for ((first) in logData) {
      println("$first: $first")
    }
  }

  init {
    log("Log is created. XmlLesson:" + lesson.name)
  }

}
