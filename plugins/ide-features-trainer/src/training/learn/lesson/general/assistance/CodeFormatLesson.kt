// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package training.learn.lesson.general.assistance

import com.intellij.codeInsight.CodeInsightBundle
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.editor.impl.EditorComponentImpl
import com.intellij.testGuiFramework.impl.button
import training.learn.LessonsBundle
import training.learn.interfaces.Module
import training.learn.lesson.kimpl.KLesson
import training.learn.lesson.kimpl.LessonContext
import training.learn.lesson.kimpl.LessonSample
import training.learn.lesson.kimpl.LessonUtil.restoreIfModifiedOrMoved

class CodeFormatLesson(module: Module, override val lang: String, private val sample: LessonSample, private val optimizeImports: Boolean) :
  KLesson("CodeAssistance.CodeFormatting", LessonsBundle.message("code.format.lesson.name"), module, lang) {

  override val lessonContent: LessonContext.() -> Unit = {
    prepareSample(sample)

    val properties = PropertiesComponent.getInstance()
    prepareRuntimeTask {
      properties.setValue("LayoutCode.optimizeImports", false)
    }

    actionTask("ReformatCode") {
      restoreIfModifiedOrMoved()
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
        test {
          properties.setValue("LayoutCode.optimizeImports", true)
          ideFrame {
            button("Run").click()
          }
        }
      }
    }
  }
}