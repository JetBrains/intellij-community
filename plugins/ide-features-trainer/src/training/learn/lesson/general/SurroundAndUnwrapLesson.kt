// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package training.learn.lesson.general

import training.dsl.*
import training.dsl.LessonUtil.checkExpectedStateOfEditor
import training.learn.LearnBundle
import training.learn.LessonsBundle
import training.learn.course.KLesson

abstract class SurroundAndUnwrapLesson
  : KLesson("Surround and unwrap", LessonsBundle.message("surround.and.unwrap.lesson.name")) {

  protected abstract val sample: LessonSample

  protected abstract val surroundItems: Array<String>
  protected abstract val lineShiftBeforeUnwrap: Int

  protected open val surroundItemName: String
    get() = surroundItems.joinToString(separator = "/")

  override val lessonContent: LessonContext.() -> Unit
    get() = {
      prepareSample(sample)

      task("SurroundWith") {
        proposeIfModified {
          editor.caretModel.currentCaret.selectionStart != previous.sample.selection?.first ||
          editor.caretModel.currentCaret.selectionEnd != previous.sample.selection?.second
        }
        text(LessonsBundle.message("surround.and.unwrap.invoke.surround", action(it)))
        triggerByListItemAndHighlight { item ->
          surroundItems.all { need -> wordIsPresent(item.toString(), need) }
        }
        test { actions(it) }
      }

      task {
        text(LessonsBundle.message("surround.and.unwrap.choose.surround.item", strong(surroundItemName)))
        stateCheck {
          editor.document.charsSequence.let { sequence ->
            surroundItems.all { sequence.contains(it) }
          }
        }
        restoreByUi()
        test {
          type("${surroundItems.joinToString(separator = " ")}\n") }
      }

      prepareRuntimeTask {
        editor.caretModel.currentCaret.moveCaretRelatively(0, lineShiftBeforeUnwrap, false, true)
      }

      prepareRuntimeTask { // restore point
        setSample(previous.sample)
      }
      task("Unwrap") {
        proposeIfModified {
          editor.caretModel.currentCaret.logicalPosition.line != previous.position.line
        }
        text(LessonsBundle.message("surround.and.unwrap.invoke.unwrap", action(it)))
        triggerByListItemAndHighlight { item ->
          wordIsPresent(item.toString(), surroundItems[0])
        }
        test { actions(it) }
      }
      task {
        restoreAfterStateBecomeFalse {
          previous.ui?.isShowing != true
        }
        text(LessonsBundle.message("surround.and.unwrap.choose.unwrap.item",
                                   strong(LearnBundle.message("surround.and.unwrap.item", surroundItems[0]))))
        stateCheck {
          editor.document.charsSequence.let { sequence ->
            !surroundItems.any { sequence.contains(it) }
          }
        }
        test { type("${surroundItems[0]}\n") }
      }
    }

  private fun wordIsPresent(text: String, word: String): Boolean {
    var index = 0
    while(index != -1 && index < text.length) {
      index = text.indexOf(word, startIndex = index)
      if (index != -1) {
        if ((index == 0 || !text[index - 1].isLetterOrDigit()) &&
            (index + word.length == text.length || !text[index + word.length + 1].isLetterOrDigit()))
          return true
        index = index + word.length
      }
    }
    return false
  }

  private fun TaskContext.proposeIfModified(checkCaret: TaskRuntimeContext.() -> Boolean) {
    proposeRestore {
      checkExpectedStateOfEditor(previous.sample, false)
      ?: if (checkCaret()) TaskContext.RestoreNotification(TaskContext.CaretRestoreProposal, callback = restorePreviousTaskCallback) else null
    }
  }
}