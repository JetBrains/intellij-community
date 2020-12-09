// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package training.learn.lesson.general.navigation

import com.intellij.CommonBundle
import com.intellij.ide.actions.Switcher
import com.intellij.ide.actions.ui.JBListWithOpenInRightSplit
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.wm.IdeFrame
import com.intellij.testGuiFramework.framework.GuiTestUtil
import com.intellij.testGuiFramework.util.Key
import com.intellij.ui.components.JBList
import com.intellij.ui.speedSearch.SpeedSearchSupply
import training.commands.kotlin.TaskRuntimeContext
import training.commands.kotlin.TaskTestContext
import training.learn.LearnBundle
import training.learn.LessonsBundle
import training.learn.interfaces.Module
import training.learn.lesson.LessonManager
import training.learn.lesson.kimpl.KLesson
import training.learn.lesson.kimpl.LessonContext
import training.learn.lesson.kimpl.LessonUtil
import training.learn.lesson.kimpl.LessonUtil.restoreIfModifiedOrMoved
import java.awt.event.KeyEvent
import javax.swing.JComponent
import kotlin.random.Random

abstract class RecentFilesLesson(module: Module, lang: String)
  : KLesson("Recent Files and Locations", LessonsBundle.message("recent.files.lesson.name"), module, lang) {

  abstract override val existedFile: String
  abstract val transitionMethodName: String
  abstract val transitionFileName: String
  abstract val stringForRecentFilesSearch: String  // should look like transitionMethodName
  abstract fun LessonContext.setInitialPosition()

  private val countOfFilesToOpen: Int = 20
  private val countOfFilesToDelete: Int = 5

  override val lessonContent: LessonContext.() -> Unit = {
    setInitialPosition()

    task("GotoDeclaration") {
      text(LessonsBundle.message("recent.files.first.transition", code(transitionMethodName), action(it)))
      trigger(it) { virtualFile.name.startsWith(transitionFileName) }
      restoreIfModifiedOrMoved()
      test { actions(it) }
    }

    waitBeforeContinue(500)

    prepareRuntimeTask {
      if (!TaskTestContext.inTestMode) {
        val userDecision = Messages.showOkCancelDialog(
          LessonsBundle.message("recent.files.dialog.message"),
          LessonsBundle.message("recent.files.dialog.title"),
          CommonBundle.message("button.ok"),
          LearnBundle.message("learn.ui.button.stop.lesson"),
          null
        )
        if(userDecision != Messages.OK) {
          LessonManager.instance.stopLesson()
        }
      }
    }

    openManyFiles()

    actionTask("RecentFiles") {
      LessonsBundle.message("recent.files.show.recent.files", action(it))
    }

    task("rfd") {
      text(LessonsBundle.message("recent.files.search.typing", code(it)))
      stateCheck { checkRecentFilesSearch(it) }
      test {
        ideFrame {
          waitComponent(Switcher.SwitcherPanel::class.java)
        }
        type(it)
      }
    }

    task {
      text(LessonsBundle.message("recent.files.search.jump", LessonUtil.rawEnter()))
      stateCheck { virtualFile.name == existedFile.substringAfterLast("/") }
      test { GuiTestUtil.shortcut(Key.ENTER) }
    }

    actionTask("RecentFiles") {
      LessonsBundle.message("recent.files.use.recent.files.again", action(it))
    }

    task {
      text(LessonsBundle.message("recent.files.delete", strong(countOfFilesToDelete.toString()), LessonUtil.rawKeyStroke(KeyEvent.VK_DELETE)))
      var initialRecentFilesCount = -1
      stateCheck {
        val focusOwner = focusOwner as? JBListWithOpenInRightSplit<*> ?: return@stateCheck false
        if (initialRecentFilesCount == -1) {
          initialRecentFilesCount = focusOwner.itemsCount
        }
        initialRecentFilesCount - focusOwner.itemsCount >= countOfFilesToDelete
      }
      test {
        repeat(countOfFilesToDelete) {
          GuiTestUtil.shortcut(Key.DELETE)
        }
      }
    }

    task {
      text(LessonsBundle.message("recent.files.close.popup", LessonUtil.rawKeyStroke(KeyEvent.VK_ESCAPE)))
      stateCheck { focusOwner is IdeFrame }
      test { GuiTestUtil.shortcut(Key.ESCAPE) }
    }

    actionTask("RecentLocations") {
      LessonsBundle.message("recent.files.show.recent.locations", action(it))
    }

    task(stringForRecentFilesSearch) {
      text(LessonsBundle.message("recent.files.locations.search.typing", code(it)))
      stateCheck { checkRecentLocationsSearch(it) }
      test {
        ideFrame {
          waitComponent(JBList::class.java)
        }
        type(it)
      }
    }

    task {
      text(LessonsBundle.message("recent.files.locations.search.jump", LessonUtil.rawEnter()))
      stateCheck { virtualFile.name != existedFile.substringAfterLast('/') }
      test { GuiTestUtil.shortcut(Key.ENTER) }
    }
  }

  // Should open (countOfFilesToOpen - 1) files
  open fun LessonContext.openManyFiles() {
    val openedFiles = mutableSetOf<String>()
    val random = Random(System.currentTimeMillis())
    for (i in 0 until (countOfFilesToOpen - 1)) {
      waitBeforeContinue(200)
      prepareRuntimeTask {
        val curFile = virtualFile
        val files = curFile.parent?.children
                    ?: throw IllegalStateException("Not found neighbour files for ${curFile.name}")

        var index = random.nextInt(0, files.size)
        while (openedFiles.contains(files[index].name)) {
          index = random.nextInt(0, files.size)
        }

        val nextFile = files[index]
        openedFiles.add(nextFile.name)
        invokeLater {
          FileEditorManager.getInstance(project).openFile(nextFile, true)
        }
      }
    }
  }

  private fun TaskRuntimeContext.checkRecentFilesSearch(expected: String): Boolean {
    val focusOwner = focusOwner?.parent?.parent?.parent ?: return false
    return focusOwner is Switcher.SwitcherPanel && checkWordInSearch(expected, focusOwner)
  }

  private fun TaskRuntimeContext.checkRecentLocationsSearch(expected: String): Boolean {
    val focusOwner = focusOwner
    return focusOwner is JBList<*> && checkWordInSearch(expected, focusOwner)
  }

  private fun checkWordInSearch(expected: String, component: JComponent): Boolean {
    val supply = SpeedSearchSupply.getSupply(component)
    val enteredPrefix = supply?.enteredPrefix ?: return false
    return enteredPrefix.equals(expected, ignoreCase = true)
  }

  override val testScriptProperties: TaskTestContext.TestScriptProperties
    get() = TaskTestContext.TestScriptProperties(duration = 20)
}