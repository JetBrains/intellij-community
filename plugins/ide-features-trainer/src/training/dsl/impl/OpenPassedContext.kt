// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package training.dsl.impl

import com.intellij.openapi.project.Project
import training.dsl.LearningBalloonConfig
import training.dsl.LessonContext
import training.dsl.TaskContext
import training.learn.course.KLesson
import training.learn.lesson.LessonManager

internal class OpenPassedContext(private val project: Project, override val lesson: KLesson) : LessonContext() {
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
