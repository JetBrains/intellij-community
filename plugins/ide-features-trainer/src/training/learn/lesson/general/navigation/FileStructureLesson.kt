// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package training.learn.lesson.general.navigation

import com.intellij.ide.dnd.aware.DnDAwareTree
import com.intellij.openapi.editor.impl.EditorComponentImpl
import com.intellij.testGuiFramework.framework.GuiTestUtil
import com.intellij.testGuiFramework.util.Key
import com.intellij.ui.speedSearch.SpeedSearchSupply
import training.commands.kotlin.TaskRuntimeContext
import training.learn.LessonsBundle
import training.learn.interfaces.Module
import training.learn.lesson.kimpl.KLesson
import training.learn.lesson.kimpl.LessonContext
import training.learn.lesson.kimpl.LessonUtil

abstract class FileStructureLesson(module: Module, lang: String)
  : KLesson("File structure", LessonsBundle.message("file.structure.lesson.name"), module, lang) {
  abstract override val existedFile: String
  abstract val searchSubstring: String
  abstract val firstWord: String
  abstract val secondWord: String

  override val lessonContent: LessonContext.() -> Unit
    get() = {
      caret(0)

      actionTask("FileStructurePopup") {
        LessonsBundle.message("file.structure.open.popup", action(it))
      }
      task(searchSubstring) {
        text(LessonsBundle.message("file.structure.request.prefixes", strong(firstWord), strong(secondWord), code(searchSubstring)))
        stateCheck { checkWordInSearch(it) }
        test {
          ideFrame {
            waitComponent(DnDAwareTree::class.java, "FileStructurePopup")
          }
          type(it)
        }
      }
      task {
        text(LessonsBundle.message("file.structure.navigate", LessonUtil.rawEnter()))
        stateCheck { focusOwner is EditorComponentImpl }
        test { GuiTestUtil.shortcut(Key.ENTER) }
      }
      task("ActivateStructureToolWindow") {
        text(LessonsBundle.message("file.structure.toolwindow", action(it)))
        stateCheck { focusOwner?.javaClass?.name?.contains("StructureViewComponent") ?: false }
        test { actions(it) }
      }
    }

  private fun TaskRuntimeContext.checkWordInSearch(expected: String): Boolean {
    val focusOwner = focusOwner
    if (focusOwner is DnDAwareTree && focusOwner.javaClass.name.contains("FileStructurePopup")) {
      val supply = SpeedSearchSupply.getSupply(focusOwner)
      return supply?.enteredPrefix == expected
    }
    return false
  }
}
