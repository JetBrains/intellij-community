// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package training.learn.lesson.kimpl

import training.commands.kotlin.TaskContext

class LessonContextImpl(private val executor: LessonExecutor): LessonContext() {
  override fun task(taskContent: TaskContext.() -> Unit) {
    executor.task(taskContent)
  }

  override fun waitBeforeContinue(delayMillis: Int) {
    executor.waitBeforeContinue(delayMillis)
  }
}
