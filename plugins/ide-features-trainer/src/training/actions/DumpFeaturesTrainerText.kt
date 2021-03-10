// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package training.actions

import com.intellij.ide.CopyPasteManagerEx
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import training.dsl.LearningBalloonConfig
import training.dsl.RuntimeTextContext
import training.dsl.TaskContext
import training.dsl.impl.ApplyTaskLessonContext
import training.learn.CourseManager
import training.learn.course.KLesson
import java.awt.datatransfer.StringSelection

class DumpFeaturesTrainerText : AnAction() {
  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    val lessonsForModules = CourseManager.instance.lessonsForModules
    val buffer = StringBuffer()
    for (x in lessonsForModules) {
      if (x is KLesson) {
        buffer.append(x.name)
        buffer.append(":\n")
        x.lessonContent(ApplyTaskLessonContext(TextCollector(buffer, project)))
        buffer.append('\n')
      }
    }
    CopyPasteManagerEx.getInstance().setContents(StringSelection(buffer.toString()))
  }
}


private class TextCollector(private val buffer: StringBuffer, override val project: Project) : TaskContext() {
  override fun text(text: String, useBalloon: LearningBalloonConfig?) {
    buffer.append(text)
    buffer.append('\n')
  }

  override fun runtimeText(callback: RuntimeTextContext.() -> String?) {
    // TODO: think how to dump it
  }
}