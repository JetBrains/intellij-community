// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package training.dsl.impl

import training.dsl.LessonContext
import training.dsl.TaskContext
import training.learn.course.KLesson

internal class LessonContextImpl(private val executor: LessonExecutor) : LessonContext() {
  override fun task(taskContent: TaskContext.() -> Unit) {
    executor.task(taskContent)
  }

  override fun waitBeforeContinue(delayMillis: Int) {
    executor.waitBeforeContinue(delayMillis)
  }

  override val lesson: KLesson = executor.lesson
}
