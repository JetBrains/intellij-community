// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package training.learn.lesson.general.assistance

import com.intellij.testGuiFramework.fixtures.IdeFrameFixture
import training.commands.kotlin.TaskRuntimeContext
import training.learn.LessonsBundle
import training.learn.interfaces.Module
import training.learn.lesson.kimpl.KLesson
import training.learn.lesson.kimpl.LessonContext
import training.learn.lesson.kimpl.LessonSample

abstract class EditorCodingAssistanceLesson(module: Module, lang: String, private val sample: LessonSample) :
  KLesson("CodeAssistance.EditorCodingAssistance", LessonsBundle.message("editor.coding.assistance.lesson.name"), module, lang) {

  protected abstract val intentionDisplayName: String

  protected abstract val variableNameToHighlight: String

  protected abstract val fixedText: String

  protected abstract fun IdeFrameFixture.simulateErrorFixing()

  override val lessonContent: LessonContext.() -> Unit = {
    prepareSample(sample)

    actionTask("GotoNextError") {
      LessonsBundle.message("editor.coding.assistance.goto.next.error", action(it))
    }

    task("ShowErrorDescription") {
      text(LessonsBundle.message("editor.coding.assistance.show.error.description", action(it)))
      trigger(it)
      test {
        Thread.sleep(500)
        actions(it)
      }
    }

    task("ShowIntentionActions") {
      text(LessonsBundle.message("editor.coding.assistance.show.intention", action(it), strong(intentionDisplayName)))
      stateCheck { editor.document.charsSequence.contains(fixedText) }
      test {
        actions(it)
        ideFrame {
          Thread.sleep(500)
          simulateErrorFixing()
        }
      }
    }

    prepareRuntimeTask {
      val offset = editor.document.charsSequence.indexOf(variableNameToHighlight)
      if (offset != -1) {
        editor.caretModel.moveToOffset(offset)
      }
    }

    task("HighlightUsagesInFile") {
      text(LessonsBundle.message("editor.coding.assistance.highlight.usages", action(it)))
      trigger(it) { checkSymbolAtCaretIsLetter() }
      test { actions(it) }
    }
  }

  private fun TaskRuntimeContext.checkSymbolAtCaretIsLetter(): Boolean {
    val caretOffset = editor.caretModel.offset
    val sequence = editor.document.charsSequence
    return caretOffset != sequence.length && sequence[caretOffset].isLetter()
  }
}