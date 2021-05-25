// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package training.learn.lesson.general.navigation

import com.intellij.ide.dnd.aware.DnDAwareTree
import com.intellij.openapi.editor.LogicalPosition
import com.intellij.openapi.editor.impl.EditorComponentImpl
import com.intellij.ui.speedSearch.SpeedSearchSupply
import training.dsl.LessonContext
import training.dsl.LessonUtil
import training.dsl.TaskRuntimeContext
import training.dsl.restoreAfterStateBecomeFalse
import training.learn.LessonsBundle
import training.learn.course.KLesson
import training.learn.course.LessonType

abstract class FileStructureLesson
  : KLesson("File structure", LessonsBundle.message("file.structure.lesson.name")) {
  abstract override val existedFile: String
  abstract val methodToFindPosition: LogicalPosition

  private val searchSubstring: String = "hosa"
  private val firstWord: String = "homo"
  private val secondWord: String = "sapience"

  override val lessonType = LessonType.SINGLE_EDITOR

  override val lessonContent: LessonContext.() -> Unit
    get() = {
      caret(0)

      actionTask("FileStructurePopup") {
        LessonsBundle.message("file.structure.open.popup", action(it))
      }
      task(searchSubstring) {
        text(LessonsBundle.message("file.structure.request.prefixes", strong(firstWord), strong(secondWord), code(searchSubstring)))
        stateCheck { checkWordInSearch(it) }
        restoreAfterStateBecomeFalse { focusOwner is EditorComponentImpl }
        test {
          ideFrame {
            waitComponent(DnDAwareTree::class.java, "FileStructurePopup")
          }
          type(it)
        }
      }
      task {
        text(LessonsBundle.message("file.structure.navigate", LessonUtil.rawEnter()))
        stateCheck { editor.caretModel.logicalPosition == methodToFindPosition }
        restoreState { !checkWordInSearch(searchSubstring) }
        test { invokeActionViaShortcut("ENTER") }
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
