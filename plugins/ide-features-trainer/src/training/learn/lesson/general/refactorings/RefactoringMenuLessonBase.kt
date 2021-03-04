// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package training.learn.lesson.general.refactorings

import com.intellij.idea.ActionsBundle
import com.intellij.refactoring.rename.inplace.InplaceRefactoring
import com.intellij.testGuiFramework.framework.GuiTestUtil
import com.intellij.testGuiFramework.util.Key
import com.intellij.ui.components.JBList
import training.commands.kotlin.TaskContext
import training.commands.kotlin.TaskRuntimeContext
import training.learn.LessonsBundle
import training.learn.interfaces.Module
import training.learn.lesson.kimpl.*
import training.learn.lesson.kimpl.LessonUtil.restoreIfModifiedOrMoved
import javax.swing.JList

abstract class RefactoringMenuLessonBase(lessonId: String, module: Module, languageId: String)
  : KLesson(lessonId, LessonsBundle.message("refactoring.menu.lesson.name"), module, languageId) {
  fun LessonContext.extractParameterTasks() {
    lateinit var showPopupTaskId: TaskContext.TaskId
    actionTask("Refactorings.QuickListPopupAction") {
      showPopupTaskId = taskId
      restoreIfModifiedOrMoved()
      LessonsBundle.message("refactoring.menu.show.refactoring.list", action(it))
    }
    task(ActionsBundle.message("action.IntroduceParameter.text").dropMnemonic()) {
      text(LessonsBundle.message("refactoring.menu.introduce.parameter", strong(it)))
      triggerByUiComponentAndHighlight(highlightBorder = false, highlightInside = false) { ui: JList<*> ->
        ui.model.size > 0 && ui.model.getElementAt(0).toString().contains(it)
      }
      restoreAfterStateBecomeFalse { focusOwner !is JBList<*> }
      test {
        type("pa")
      }
    }

    task("IntroduceParameter") {
      text(LessonsBundle.message("refactoring.menu.start.refactoring",
                                 action("EditorChooseLookupItem"), LessonUtil.actionName(it)))
      trigger(it)
      stateCheck { hasInplaceRename() }
      restoreState(restoreId = showPopupTaskId) { focusOwner !is JBList<*> }
      test {
        GuiTestUtil.shortcut(Key.ENTER)
      }
    }

    task {
      text(LessonsBundle.message("refactoring.menu.finish.refactoring", LessonUtil.rawEnter()))
      stateCheck {
        !hasInplaceRename()
      }
      test {
        GuiTestUtil.shortcut(Key.ENTER)
      }
    }
  }

  private fun TaskRuntimeContext.hasInplaceRename() = editor.getUserData(InplaceRefactoring.INPLACE_RENAMER) != null
}
