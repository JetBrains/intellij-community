// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package training.dsl.impl

import training.dsl.LessonContext
import training.dsl.TaskContext

internal class ApplyTaskLessonContext(private val taskContext: TaskContext) : LessonContext() {
  override fun task(taskContent: TaskContext.() -> Unit) {
    taskContent(taskContext)
  }
}
