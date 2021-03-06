// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package training.learn.lesson.general.navigation

import com.intellij.find.FindBundle
import com.intellij.find.FindInProjectSettings
import com.intellij.find.FindManager
import com.intellij.find.SearchTextArea
import com.intellij.find.impl.FindInProjectSettingsBase
import com.intellij.find.impl.FindPopupPanel
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.usages.UsagePresentation
import com.intellij.util.ui.UIUtil
import org.fest.swing.core.MouseClickInfo
import org.fest.swing.data.TableCell
import org.fest.swing.fixture.JTableFixture
import org.fest.swing.fixture.JTextComponentFixture
import training.dsl.*
import training.learn.LessonsBundle
import training.learn.course.KLesson
import training.ui.LearningUiUtil.findComponentWithTimeout
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import javax.swing.*

class FindInFilesLesson(override val existedFile: String)
  : KLesson("Find in files", LessonsBundle.message("find.in.files.lesson.name")) {

  override val lessonContent: LessonContext.() -> Unit = {
    resetFindSettings()

    lateinit var showPopupTaskId: TaskContext.TaskId
    task("FindInPath") {
      showPopupTaskId = taskId
      text(LessonsBundle.message("find.in.files.show.find.popup",
                                 action(it), LessonUtil.actionName(it)))
      triggerByUiComponentAndHighlight(false, false) { popup: FindPopupPanel ->
        !popup.helper.isReplaceState
      }
      test {
        actions(it)
      }
    }

    task("apple") {
      text(LessonsBundle.message("find.in.files.type.to.find", code(it)))
      stateCheck { getFindPopup()?.stringToFind?.toLowerCase() == it }
      restoreByUi()
      test { type(it) }
    }

    task {
      val wholeWordsButtonText = FindBundle.message("find.whole.words").dropMnemonic()
      text(LessonsBundle.message("find.in.files.whole.words",
                                 icon(AllIcons.Actions.Words),
                                 LessonUtil.rawKeyStroke(KeyStroke.getKeyStroke(KeyEvent.VK_W, InputEvent.ALT_DOWN_MASK))))
      highlightAndTriggerWhenButtonSelected(wholeWordsButtonText)
      showWarningIfPopupClosed(false)
      test(waitEditorToBeReady = false) {
        ideFrame {
          actionButton(wholeWordsButtonText).click()
        }
      }
    }

    task("apple...") {
      text(LessonsBundle.message("find.in.files.select.row",
                                 action("EditorUp"), action("EditorDown")))
      triggerByPartOfComponent { table: JTable ->
        val rowIndex = table.findLastRowIndexOfItemWithText(it)
        if (rowIndex >= 0) {
          table.getCellRect(rowIndex, 0, false)
        }
        else null
      }
      triggerByUiComponentAndHighlight(false, false) { table: JTable ->
        table.selectedRow != -1 && table.selectedRow == table.findLastRowIndexOfItemWithText(it)
      }
      restoreByUi(restoreId = showPopupTaskId)
      test {
        ideFrame {
          val table = findComponentWithTimeout { table: JTable -> table.findLastRowIndexOfItemWithText(it) != -1 }
          val tableFixture = JTableFixture(robot(), table)
          val rowIndex = table.findLastRowIndexOfItemWithText(it)
          tableFixture.click(TableCell.row(rowIndex).column(0), MouseClickInfo.leftButton())
        }
      }
    }

    task {
      text(LessonsBundle.message("find.in.files.go.to.file", LessonUtil.rawEnter()))
      stateCheck { virtualFile.name != existedFile.substringAfterLast('/') }
      restoreByUi(restoreId = showPopupTaskId)
      test { invokeActionViaShortcut("ENTER") }
    }

    task("ReplaceInPath") {
      text(LessonsBundle.message("find.in.files.show.replace.popup",
                                 action(it), LessonUtil.actionName(it)))
      triggerByUiComponentAndHighlight(false, false) { popup: FindPopupPanel ->
        popup.helper.isReplaceState
      }
      test { actions(it) }
    }

    task("orange") {
      text(LessonsBundle.message("find.in.files.type.to.replace",
                                 code("apple"), code(it)))
      triggerByUiComponentAndHighlight(highlightInside = false) { ui: SearchTextArea ->
        it.startsWith(ui.textArea.text)
      }
      stateCheck {
        getFindPopup()?.helper?.model?.let { model ->
          model.stringToReplace == it && model.stringToFind == "apple"
        } ?: false
      }
      restoreByUi()
      test {
        ideFrame {
          val textArea = findComponentWithTimeout { textArea: JTextArea -> textArea.text == "" }
          JTextComponentFixture(robot(), textArea).click()
          type(it)
        }
      }
    }

    task {
      val directoryScopeText = FindBundle.message("find.popup.scope.directory").dropMnemonic()
      text(LessonsBundle.message("find.in.files.select.directory",
                                 strong(directoryScopeText),
                                 LessonUtil.rawKeyStroke(KeyStroke.getKeyStroke(KeyEvent.VK_D, InputEvent.ALT_DOWN_MASK))))
      highlightAndTriggerWhenButtonSelected(directoryScopeText)
      showWarningIfPopupClosed(true)
      test {
        ideFrame {
          actionButton(directoryScopeText).click()
        }
      }
    }

    val replaceAllDialogTitle = FindBundle.message("find.replace.all.confirmation.title")
    task {
      val replaceAllButtonText = FindBundle.message("find.popup.replace.all.button").dropMnemonic()
      text(LessonsBundle.message("find.in.files.press.replace.all", strong(replaceAllButtonText)))
      triggerByUiComponentAndHighlight { button: JButton ->
        button.text == replaceAllButtonText
      }
      triggerByUiComponentAndHighlight(false, false) { dialog: JDialog ->
        dialog.title == replaceAllDialogTitle
      }
      showWarningIfPopupClosed(true)
      test {
        ideFrame {
          button(replaceAllButtonText).click()
        }
      }
    }

    task {
      val replaceButtonText = FindBundle.message("find.replace.command")
      text(LessonsBundle.message("find.in.files.confirm.replace", strong(replaceButtonText)))
      stateCheck { editor.document.charsSequence.contains("orange") }
      restoreByUi(delayMillis = defaultRestoreDelay)
      test(waitEditorToBeReady = false) {
        dialog(title = "Replace All") {
          button(replaceButtonText).click()
        }
      }
    }
  }

  private fun TaskRuntimeContext.getFindPopup(): FindPopupPanel? {
    return UIUtil.getParentOfType(FindPopupPanel::class.java, focusOwner)
  }

  private fun TaskContext.highlightAndTriggerWhenButtonSelected(buttonText: String) {
    triggerByUiComponentAndHighlight { button: ActionButton ->
      button.action.templateText == buttonText
    }
    triggerByUiComponentAndHighlight(false, false) { button: ActionButton ->
      button.action.templateText == buttonText && button.isSelected
    }
  }

  private fun JTable.findLastRowIndexOfItemWithText(textToFind: String): Int {
    for (ind in (rowCount - 1) downTo 0) {
      val item = getValueAt(ind, 0) as? UsagePresentation
      if (item?.plainText?.contains(textToFind, true) == true) {
        return ind
      }
    }
    return -1
  }

  private fun TaskContext.showWarningIfPopupClosed(isReplacePopup: Boolean) {
    val actionId = if (isReplacePopup) "ReplaceInPath" else "FindInPath"
    showWarning(LessonsBundle.message("find.in.files.popup.closed.warning.message", action(actionId), LessonUtil.actionName(actionId)),
                restoreTaskWhenResolved = true) {
      getFindPopup()?.helper?.isReplaceState != isReplacePopup
    }
  }

  private fun LessonContext.resetFindSettings() {
    prepareRuntimeTask {
      FindManager.getInstance(project).findInProjectModel.apply {
        isWholeWordsOnly = false
        stringToFind = ""
        stringToReplace = ""
        directoryName = null
      }
      val settings = FindInProjectSettings.getInstance(project) as? FindInProjectSettingsBase
      settings?.apply {
        findStrings.clear()
        replaceStrings.clear()
        recentDirectories.clear()
      }
    }
  }

  override val testScriptProperties = TaskTestContext.TestScriptProperties(10)
}