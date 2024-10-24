// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package training.learn.lesson.general.navigation

import com.intellij.ide.dnd.aware.DnDAwareTree
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.editor.LogicalPosition
import com.intellij.openapi.editor.impl.EditorComponentImpl
import com.intellij.ui.speedSearch.SpeedSearchSupply
import training.dsl.*
import training.learn.LessonsBundle
import training.learn.course.KLesson
import training.learn.course.LessonType
import training.util.isToStringContains

abstract class FileStructureLesson
  : KLesson("File structure", LessonsBundle.message("file.structure.lesson.name")) {
  abstract override val sampleFilePath: String
  abstract val methodToFindPosition: LogicalPosition

  private val searchSubstring: String = "hosa"
  private val firstWord: String = "Homo"
  private val secondWord: String = "Sapiens"

  override val lessonType = LessonType.SINGLE_EDITOR

  override val lessonContent: LessonContext.() -> Unit
    get() = {
      sdkConfigurationTasks()

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
        stateCheck { previous.ui?.isShowing != true && editor.caretModel.logicalPosition == methodToFindPosition }
        test { invokeActionViaShortcut("ENTER") }
      }
      // There is no Structure tool window in the PyCharm Edu. So added this check.
      @Suppress("UnresolvedPluginConfigReference") // todo IJPL-165055
      if (ActionManager.getInstance().getAction("ActivateStructureToolWindow") != null) {
        task("ActivateStructureToolWindow") {
          text(LessonsBundle.message("file.structure.toolwindow", action(it)))
          stateCheck { focusOwner?.javaClass?.name.isToStringContains("StructureViewComponent") }
          test { actions(it) }
        }
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

  override val helpLinks: Map<String, String> get() = mapOf(
    Pair(LessonsBundle.message("file.structure.help.link"),
         LessonUtil.getHelpLink("viewing-structure-of-a-source-file.html")),
  )
}
