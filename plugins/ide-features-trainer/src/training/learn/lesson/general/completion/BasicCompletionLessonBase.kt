// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package training.learn.lesson.general.completion

import com.intellij.testGuiFramework.framework.GuiTestUtil
import com.intellij.testGuiFramework.util.Key
import training.learn.LessonsBundle
import training.learn.interfaces.Module
import training.learn.lesson.kimpl.KLesson
import training.learn.lesson.kimpl.LessonContext
import training.learn.lesson.kimpl.LessonSample
import training.learn.lesson.kimpl.LessonUtil
import training.learn.lesson.kimpl.LessonUtil.checkExpectedStateOfEditor
import javax.swing.JList

abstract class BasicCompletionLessonBase(module: Module, lang: String)
  : KLesson("Basic completion", LessonsBundle.message("basic.completion.lesson.name"), module, lang) {
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
        triggerByListItemAndHighlight(highlightBorder = false, highlightInside = false) { // no highlighting
          it.toString().contains(item1Completion)
        }
        proposeRestore {
          checkExpectedStateOfEditor(sample1) {
            val change = if (it.endsWith(item1CompletionSuffix)) it.subSequence(0, it.length - item1CompletionSuffix.length) else it
            item1CompletionPrefix.substring(0, item1CompletionPrefix.length - 2).startsWith(change)
          }
        }
        test {
          GuiTestUtil.typeText(item1StartToType)
        }
      }
      task {
        text(LessonsBundle.message("basic.completion.continue.typing", code(item1Completion)))
        stateCheck {
          (previous.ui as? JList<*>)?.let {
            isTheFirstVariant(it)
          } ?: false
        }
        restoreByUi()
      }
      task("EditorChooseLookupItem") {
        text(LessonsBundle.message("basic.completion.just.press.to.complete", action(it)))
        trigger(it) {
          editor.document.text == result1
        }
        restoreState {
          (previous.ui as? JList<*>)?.takeIf { ui -> ui.isShowing }?.let { ui ->
            !isTheFirstVariant(ui)
          } ?: true
        }
        test {
          GuiTestUtil.shortcut(Key.ENTER)
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