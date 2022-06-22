// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package training.learn.lesson.general.refactorings

import com.intellij.idea.ActionsBundle
import com.intellij.openapi.editor.impl.EditorComponentImpl
import com.intellij.refactoring.RefactoringBundle
import com.intellij.refactoring.rename.inplace.InplaceRefactoring
import com.intellij.ui.EngravedLabel
import training.dsl.*
import training.dsl.LessonUtil.restoreIfModifiedOrMoved
import training.learn.LessonsBundle
import training.learn.course.KLesson
import training.util.adaptToNotNativeLocalization
import training.util.isToStringContains
import javax.swing.JList

abstract class RefactoringMenuLessonBase(lessonId: String) : KLesson(lessonId, LessonsBundle.message("refactoring.menu.lesson.name")) {
  protected abstract val sample: LessonSample

  fun LessonContext.extractParameterTasks() {
    prepareSample(sample)

    showWarningIfInplaceRefactoringsDisabled()

    lateinit var showPopupTaskId: TaskContext.TaskId
    task("Refactorings.QuickListPopupAction") {
      showPopupTaskId = taskId
      text(LessonsBundle.message("refactoring.menu.show.refactoring.list", action(it)))
      val refactorThisTitle = RefactoringBundle.message("refactor.this.title")
      triggerUI().component { ui: EngravedLabel ->
        ui.text.isToStringContains(refactorThisTitle)
      }
      restoreIfModifiedOrMoved(sample)
      test { actions(it) }
    }

    if (TaskTestContext.inTestMode) {
      task {
        stateCheck {
          focusOwner !is EditorComponentImpl
        }
      }
    }

    if (!adaptToNotNativeLocalization) {
      task(ActionsBundle.message("action.IntroduceParameter.text").dropMnemonic()) {
        text(LessonsBundle.message("refactoring.menu.introduce.parameter.eng", strong(it)))
        triggerUI().component { ui: JList<*> ->
          ui.model.size > 0 && ui.model.getElementAt(0).isToStringContains(it)
        }
        restoreByUi(restoreId = showPopupTaskId, delayMillis = defaultRestoreDelay)
        test {
          type("pa")
        }
      }
    }

    task("IntroduceParameter") {
      val message = if (adaptToNotNativeLocalization) {
        LessonsBundle.message("refactoring.menu.introduce.parameter",
                              strong(ActionsBundle.message("action.IntroduceParameter.text").dropMnemonic()), LessonUtil.rawEnter())
      }
      else LessonsBundle.message("refactoring.menu.start.refactoring", action("EditorChooseLookupItem"), LessonUtil.actionName(it))
      text(message)
      trigger(it)
      stateCheck { hasInplaceRename() }
      restoreByUi(restoreId = showPopupTaskId, delayMillis = defaultRestoreDelay)
      test {
        invokeActionViaShortcut("ENTER")
      }
    }

    task {
      text(LessonsBundle.message("refactoring.menu.finish.refactoring", LessonUtil.rawEnter()))
      stateCheck {
        !hasInplaceRename()
      }
      test(waitEditorToBeReady = false) {
        invokeActionViaShortcut("ENTER")
      }
    }
  }

  private fun TaskRuntimeContext.hasInplaceRename() = editor.getUserData(InplaceRefactoring.INPLACE_RENAMER) != null

  override val suitableTips = listOf("RefactorThis")

  override val helpLinks: Map<String, String> get() = mapOf(
    Pair(LessonsBundle.message("refactoring.menu.help.link"),
         LessonUtil.getHelpLink("refactoring-source-code.html#refactoring_invoke")),
  )
}
