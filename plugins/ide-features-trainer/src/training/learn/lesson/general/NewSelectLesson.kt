// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package training.learn.lesson.general

import training.dsl.*
import training.dsl.LessonUtil.checkPositionOfEditor
import training.dsl.LessonUtil.restoreIfModifiedOrMoved
import training.learn.LessonsBundle
import training.learn.course.KLesson

abstract class NewSelectLesson : KLesson("Select", LessonsBundle.message("selection.lesson.name")) {

  protected val firstString = "first string"
  protected val thirdString = "third string"

  protected val beginString = "Begin of the work"
  protected val endString = "End of the work"

  protected val selectWord = "that"
  protected val selectString = "This is a long string that you can select for refactoring"
  protected abstract val selectArgument: String
  protected abstract val selectCall: String
  protected abstract val sample: LessonSample
  protected abstract val selectIf: String

  private val startCaretText: String = "at you"

  protected abstract val numberOfSelectsForWholeCall: Int

  /**
   * This method is needed for a workaround for the Rider:
   * its 'EditorSelectWord' reports it is finished before the selection is actually set in the IDE
   */
  open fun TaskContext.thisTrigger(actionId: String, checkState: TaskRuntimeContext.() -> Boolean) {
    trigger(actionId, checkState)
  }

  override val lessonContent: LessonContext.() -> Unit = {
    prepareSample(sample)
    caret(startCaretText)

    task("EditorSelectWord") {
      restoreIfModifiedOrMoved()
      text(LessonsBundle.message("new.selection.select.word", action(it)))
      thisTrigger(it) {
        checkSelection(selectWord)
      }
      test { actions(it) }
    }

    task("EditorSelectWord") {
      proposeRestoreIfWrongPreviousSelection()
      text(LessonsBundle.message("new.selection.select.string", action(it)))
      thisTrigger(it) {
        checkSelection(selectString)
      }
      test { actions(it) }
    }

    task("EditorSelectWord") {
      proposeRestoreIfWrongPreviousSelection()
      text(LessonsBundle.message("new.selection.add.quotes", action(it)))
      thisTrigger(it) {
        checkSelection(selectArgument)
      }
      test { actions(it) }
    }

    continueLesson()
  }

  protected fun LessonContext.continueLesson() {
    task("EditorSelectWord") {
      proposeRestoreIfWrongPreviousSelection()
      text(LessonsBundle.message("new.selection.select.call", action(it), numberOfSelectsForWholeCall))
      thisTrigger(it) {
        checkSelection(selectCall)
      }
      test { for (i in 1..numberOfSelectsForWholeCall) actions(it) }
    }

    actionTask("EditorUnSelectWord") {
      restoreIfModifiedOrMoved()
      LessonsBundle.message("new.selection.unselect", action(it))
    }

    waitBeforeContinue(750)

    prepareRuntimeTask {
      editor.selectionModel.removeSelection()
    }
    caret("if")

    task("EditorSelectWord") {
      proposeRestoreIfWrongPreviousSelection()
      text(LessonsBundle.message("new.selection.select.if", code("if"), action(it)))
      thisTrigger(it) {
        checkSelection(selectIf)
      }
      test { for (i in 1..2) actions(it) }
    }
  }

  protected fun TaskContext.proposeRestoreIfWrongPreviousSelection() {
    proposeRestore {
      checkPositionOfEditor(previous.sample) l@{ s ->
        val previousSelection = s.selection ?: return@l true
        val currentCaret = editor.caretModel.currentCaret
        currentCaret.selectionStart <= previousSelection.first && currentCaret.selectionEnd >= previousSelection.second
      }
    }
  }

  protected fun TaskRuntimeContext.checkSelection(needSelection: String): Boolean {
    val selectionModel = editor.selectionModel
    val selection = editor.document.charsSequence.subSequence(selectionModel.selectionStart, selectionModel.selectionEnd)
    return selection.toString().trim() == needSelection.trim()
  }

  override val helpLinks: Map<String, String> get() = mapOf(
    Pair(LessonsBundle.message("selection.help.select.code.constructs"),
         LessonUtil.getHelpLink("working-with-source-code.html#editor_code_selection")),
  )
}
