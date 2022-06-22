// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package training.learn.lesson.general.refactorings

import com.intellij.CommonBundle
import com.intellij.refactoring.RefactoringBundle
import com.intellij.refactoring.extractMethod.ExtractMethodHelper
import com.intellij.ui.UIBundle
import training.dsl.*
import training.dsl.LessonUtil.restoreIfModifiedOrMoved
import training.learn.LessonsBundle
import training.learn.course.KLesson
import javax.swing.JDialog

class ExtractMethodCocktailSortLesson(private val sample: LessonSample)
  : KLesson("Extract method", LessonsBundle.message("extract.method.lesson.name")) {
  override val lessonContent: LessonContext.() -> Unit
    get() = {
      prepareSample(sample)

      val extractMethodDialogTitle = RefactoringBundle.message("extract.method.title")
      lateinit var startTaskId: TaskContext.TaskId
      task("ExtractMethod") {
        startTaskId = taskId
        text(LessonsBundle.message("extract.method.invoke.action", action(it)))
        triggerUI().component { dialog: JDialog ->
          dialog.title == extractMethodDialogTitle
        }
        restoreIfModifiedOrMoved(sample)
        test { actions(it) }
      }
      // Now will be open the first dialog

      val okButtonText = CommonBundle.getOkButtonText()
      val yesButtonText = CommonBundle.getYesButtonText().dropMnemonic()
      val replaceFragmentDialogTitle = RefactoringBundle.message("replace.fragment")
      task {
        text(LessonsBundle.message("extract.method.start.refactoring", strong(okButtonText)))

        // Wait until the first dialog will be gone but we st
        stateCheck {
          val ui = previous.ui ?: return@stateCheck false
          !ui.isShowing && insideRefactoring()
        }

        restoreByUi(delayMillis = defaultRestoreDelay)
        test(waitEditorToBeReady = false) {
          dialog(extractMethodDialogTitle) {
            button(okButtonText).click()
          }
        }
      }

      task {
        text(LessonsBundle.message("extract.method.confirm.several.replaces", strong(yesButtonText)))

        // Wait until the third dialog
        triggerUI().component { dialog: JDialog ->
          dialog.title == replaceFragmentDialogTitle
        }
        restoreState(restoreId = startTaskId, delayMillis = defaultRestoreDelay) {
          !insideRefactoring()
        }
        test(waitEditorToBeReady = false) {
          dialog(extractMethodDialogTitle) {
            button(yesButtonText).click()
          }
        }
      }
      task {
        text(LessonsBundle.message("extract.method.second.fragment"))

        stateCheck {
          previous.ui?.isShowing?.not() ?: true
        }

        test(waitEditorToBeReady = false) {
          dialog(replaceFragmentDialogTitle) {
            button(UIBundle.message("replace.prompt.replace.button").dropMnemonic()).click()
          }
        }
      }
    }

  private fun insideRefactoring() = Thread.currentThread().stackTrace.any {
    it.className.contains(ExtractMethodHelper::class.java.simpleName)
  }

  override val suitableTips = listOf("ExtractMethod")

  override val helpLinks: Map<String, String> get() = mapOf(
    Pair(LessonsBundle.message("extract.method.help.link"),
         LessonUtil.getHelpLink("extract-method.html")),
  )
}
