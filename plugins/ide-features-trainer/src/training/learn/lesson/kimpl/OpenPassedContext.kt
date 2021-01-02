// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package training.learn.lesson.kimpl

import com.intellij.openapi.project.Project
import training.commands.kotlin.TaskContext
import training.learn.lesson.LessonManager

class OpenPassedContext(private val project: Project) : LessonContext() {
  override fun task(taskContent: TaskContext.() -> Unit) {
    OpenPassedTaskContext(project).apply(taskContent)
  }
}

private class OpenPassedTaskContext(override val project: Project) : TaskContext() {
  override fun text(text: String, useBalloon: LearningBalloonConfig?) {
    LessonManager.instance.addMessage(text)
    LessonManager.instance.passExercise()
  }
}
