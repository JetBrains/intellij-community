// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package training.learn.lesson.general.completion

import training.dsl.LessonContext
import training.dsl.LessonSample
import training.dsl.LessonUtil
import training.dsl.LessonUtil.checkExpectedStateOfEditor
import training.dsl.restoreAfterStateBecomeFalse
import training.learn.LessonsBundle
import training.learn.course.KLesson
import javax.swing.JList

abstract class BasicCompletionLessonBase : KLesson("Basic completion", LessonsBundle.message("basic.completion.lesson.name")) {
  protected abstract val sample1: LessonSample
  protected abstract val sample2: LessonSample

  protected abstract val item1StartToType: String

  protected abstract val item1CompletionPrefix: String
  protected open val item1CompletionSuffix: String = ""

  private val item1Completion: String
    get() = item1CompletionPrefix + item1CompletionSuffix

  protected abstract val item2Completion: String

  protected open val item2Inserted: String
    get() = item2Completion

  override val lessonContent: LessonContext.() -> Unit
    get() = {
      val result1 = LessonUtil.insertIntoSample(sample1, item1Completion)
      prepareSample(sample1)
      task {
        text(LessonsBundle.message("basic.completion.start.typing",
                                   code(item1Completion)))
        triggerByUiComponentAndHighlight(false, false) { ui: JList<*> ->
          isTheFirstVariant(ui)
        }
        proposeRestore {
          checkExpectedStateOfEditor(sample1) {
            val change = if (it.endsWith(item1CompletionSuffix)) it.subSequence(0, it.length - item1CompletionSuffix.length) else it
            item1CompletionPrefix.substring(0, item1CompletionPrefix.length - 2).startsWith(change)
          }
        }
        test {
          type(item1StartToType)
        }
      }
      task("EditorChooseLookupItem") {
        text(LessonsBundle.message("basic.completion.just.press.to.complete", action(it)))
        trigger(it) {
          editor.document.text == result1
        }
        restoreAfterStateBecomeFalse {
          (previous.ui as? JList<*>)?.takeIf { ui -> ui.isShowing }?.let { ui ->
            !isTheFirstVariant(ui)
          } ?: true
        }
        test(waitEditorToBeReady = false) {
          invokeActionViaShortcut("ENTER")
        }
      }
      waitBeforeContinue(500)
      val result2 = LessonUtil.insertIntoSample(sample2, item2Inserted)
      prepareSample(sample2)
      task("CodeCompletion") {
        text(LessonsBundle.message("basic.completion.activate.explicitly", action(it)))
        trigger(it)
        triggerByListItemAndHighlight { item ->
          item.toString().contains(item2Completion)
        }
        proposeRestore {
          checkExpectedStateOfEditor(sample2) { change ->
            change.isEmpty()
          }
        }
        test {
          actions(it)
        }
      }
      task {
        text(LessonsBundle.message("basic.completion.finish.explicit.completion",
                                   code(item2Completion), action("EditorChooseLookupItem")))
        stateCheck {
          editor.document.text == result2
        }
        restoreByUi()
        test {
          ideFrame {
            jListContains(item2Completion).item(item2Completion).doubleClick()
          }
        }
      }
    }

  private fun isTheFirstVariant(it: JList<*>) =
    it.model.size >= 1 && it.model.getElementAt(0).toString().contains(item1Completion)
}