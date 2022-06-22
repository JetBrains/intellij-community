// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package training.learn.lesson.general.assistance

import com.intellij.codeInsight.CodeInsightBundle
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.editor.impl.EditorComponentImpl
import training.dsl.LessonContext
import training.dsl.LessonSample
import training.dsl.LessonUtil
import training.dsl.LessonUtil.restoreIfModifiedOrMoved
import training.dsl.restoreAfterStateBecomeFalse
import training.learn.LessonsBundle
import training.learn.course.KLesson

class CodeFormatLesson(private val sample: LessonSample, private val optimizeImports: Boolean) :
  KLesson("CodeAssistance.CodeFormatting", LessonsBundle.message("code.format.lesson.name")) {

  override val lessonContent: LessonContext.() -> Unit = {
    prepareSample(sample)

    val properties = PropertiesComponent.getInstance()
    prepareRuntimeTask {
      properties.setValue("LayoutCode.optimizeImports", false)
    }

    actionTask("ReformatCode") {
      restoreIfModifiedOrMoved(sample)
      LessonsBundle.message("code.format.reformat.selection", action(it))
    }

    prepareRuntimeTask {
      editor.selectionModel.removeSelection()
    }

    task("ReformatCode") {
      text(LessonsBundle.message("code.format.reformat.file", action(it)))
      trigger(it) { editor.selectionModel.selectedText == null }
      test {
        actions(it)
      }
    }

    if (optimizeImports) {
      task("ShowReformatFileDialog") {
        text(LessonsBundle.message("code.format.show.reformat.file.dialog", action(it)))
        triggerStart(it)
        test {
          actions(it)
        }
      }

      task {
        val runButtonText = CodeInsightBundle.message("reformat.code.accept.button.text")
        val optimizeImportsActionText = CodeInsightBundle.message("process.optimize.imports")
        text(LessonsBundle.message("code.format.optimize.imports", strong(optimizeImportsActionText), strong(runButtonText)))
        stateCheck {
          focusOwner is EditorComponentImpl && properties.getBoolean("LayoutCode.optimizeImports")
        }
        restoreAfterStateBecomeFalse {
          focusOwner is EditorComponentImpl
        }
        test(waitEditorToBeReady = false) {
          properties.setValue("LayoutCode.optimizeImports", true)
          ideFrame {
            button("Run").click()
          }
        }
      }
    }
  }

  override val suitableTips = listOf("LayoutCode")

  override val helpLinks: Map<String, String> get() = mapOf(
    Pair(LessonsBundle.message("code.format.help.link"),
         LessonUtil.getHelpLink("configuring-code-style.html")),
  )
}