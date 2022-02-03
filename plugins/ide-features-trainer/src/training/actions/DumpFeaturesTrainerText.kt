// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:Suppress("HardCodedStringLiteral")

package training.actions

import com.intellij.ide.CopyPasteManagerEx
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.dsl.builder.bind
import com.intellij.ui.dsl.builder.panel
import training.dsl.LearningBalloonConfig
import training.dsl.LessonContext
import training.dsl.RuntimeTextContext
import training.dsl.TaskContext
import training.dsl.impl.LessonExecutorUtil
import training.learn.CourseManager
import training.learn.course.KLesson
import java.awt.datatransfer.StringSelection
import javax.swing.JComponent


private enum class IftDumpMode { TEXT_ONLY, CODE_POSITIONS }

private class DumpFeaturesTrainerText : DumbAwareAction() {
  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return

    val dialog = object : DialogWrapper(project) {
      var mode = IftDumpMode.TEXT_ONLY

      override fun createCenterPanel(): JComponent = panel {
        buttonsGroup {
          row {
            radioButton("Text only", IftDumpMode.TEXT_ONLY)
            radioButton("Code positions", IftDumpMode.CODE_POSITIONS)
          }
        }.bind(::mode)
      }
      init {
        init()
        title = "What Do You Want to Copy?"
      }
    }

    dialog.show()

    val lessonsForModules = CourseManager.instance.lessonsForModules
    val buffer = StringBuffer()
    for (x in lessonsForModules) {
      if (x is KLesson) {
        buffer.append(x.name)
        buffer.append(":\n")
        x.lessonContent(ApplyTaskLessonContext(buffer, project, dialog.mode))
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

private class ApplyTaskLessonContext(private val buffer: StringBuffer, private val project: Project, private val mode: IftDumpMode) : LessonContext() {
  private var internalTaskNumber = 0
  private var taskVisualIndex = 1

  override fun task(taskContent: TaskContext.() -> Unit) {
    buffer.append("($internalTaskNumber -> $taskVisualIndex) ")
    if (mode == IftDumpMode.TEXT_ONLY) {
      val taskContext: TaskContext = TextCollector(buffer, project)
      taskContent(taskContext)
    }
    if (mode == IftDumpMode.CODE_POSITIONS) {
      buffer.append(LessonExecutorUtil.getTaskCallInfo())
      buffer.append('\n')
    }

    val taskProperties = LessonExecutorUtil.taskProperties(taskContent, project)
    if (taskProperties.hasDetection && taskProperties.messagesNumber > 0) {
      taskVisualIndex++
    }
    internalTaskNumber++
  }
}