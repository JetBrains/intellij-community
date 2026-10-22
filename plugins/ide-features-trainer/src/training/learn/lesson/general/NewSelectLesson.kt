// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package training.learn.lesson.general

import training.dsl.LessonContext
import training.dsl.LessonSample
import training.dsl.LessonUtil
import training.dsl.LessonUtil.checkPositionOfEditor
import training.dsl.LessonUtil.restoreIfModifiedOrMoved
import training.dsl.TaskContext
import training.dsl.TaskRuntimeContext
import training.dsl.TaskTestContext
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

  /**
   * Set it to `false` when the editor highlighting can take more than the test timeout.
   * Then the test actions do not wait for the highlighting.
   */
  protected open val waitEditorToBeReadyInTests: Boolean = true

  /**
   * Runs [actionId] [times] times in the test mode.
   * Override it when the selection action is asynchronous.
   */
  protected open fun TaskTestContext.selectWordInTest(actionId: String, times: Int = 1) {
    repeat(times) { actions(actionId) }
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
      test(waitEditorToBeReadyInTests) { selectWordInTest(it) }
    }

    task("EditorSelectWord") {
      proposeRestoreIfWrongPreviousSelection()
      text(LessonsBundle.message("new.selection.select.string", action(it)))
      thisTrigger(it) {
        checkSelection(selectString)
      }
      test(waitEditorToBeReadyInTests) { selectWordInTest(it) }
    }

    task("EditorSelectWord") {
      proposeRestoreIfWrongPreviousSelection()
      text(LessonsBundle.message("new.selection.add.quotes", action(it)))
      thisTrigger(it) {
        checkSelection(selectArgument)
      }
      test(waitEditorToBeReadyInTests) { selectWordInTest(it) }
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
      test(waitEditorToBeReadyInTests) { selectWordInTest(it, numberOfSelectsForWholeCall) }
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
      test(waitEditorToBeReadyInTests) { selectWordInTest(it, 2) }
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
