// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package training.learn.lesson.general.navigation

import com.intellij.find.FindBundle
import com.intellij.find.FindInProjectSettings
import com.intellij.find.FindManager
import com.intellij.find.SearchTextArea
import com.intellij.find.impl.FindInProjectSettingsBase
import com.intellij.find.impl.FindPopupPanel
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.project.Project
import com.intellij.usages.UsagePresentation
import com.intellij.util.ui.UIUtil
import org.assertj.swing.core.MouseClickInfo
import org.assertj.swing.data.TableCell
import org.assertj.swing.fixture.JTableFixture
import org.assertj.swing.fixture.JTextComponentFixture
import training.dsl.*
import training.learn.LessonsBundle
import training.learn.course.KLesson
import training.ui.LearningUiUtil.findComponentWithTimeout
import training.util.isToStringContains
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import javax.swing.*

class FindInFilesLesson(override val existedFile: String)
  : KLesson("Find in files", LessonsBundle.message("find.in.files.lesson.name")) {

  override val lessonContent: LessonContext.() -> Unit = {
    prepareRuntimeTask {
      resetFindSettings(project)
    }

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

    val neededText = "apple..."
    task {
      triggerByPartOfComponent { table: JTable ->
        val rowIndex = table.findLastRowIndexOfItemWithText(neededText)
        if (rowIndex >= 0) {
          table.getCellRect(rowIndex, 0, false)
        }
        else null
      }
      restoreByTimer(1000)
      transparentRestore = true
    }

    task {
      text(LessonsBundle.message("find.in.files.select.row",
                                 action("EditorUp"), action("EditorDown")))
      stateCheck {
        isSelectedNeededItem(neededText)
      }
      restoreByUi(restoreId = showPopupTaskId)
      test {
        ideFrame {
          val table = previous.ui as? JTable ?: error("No table")
          val tableFixture = JTableFixture(robot(), table)
          val rowIndex = { table.findLastRowIndexOfItemWithText(neededText) }
          tableFixture.pointAt(TableCell.row(rowIndex()).column(0)) // It seems, the list may change for a while
          tableFixture.click(TableCell.row(rowIndex()).column(0), MouseClickInfo.leftButton())
        }
      }
    }

    task {
      text(LessonsBundle.message("find.in.files.go.to.file", LessonUtil.rawEnter()))
      stateCheck { virtualFile.name != existedFile.substringAfterLast('/') }
      restoreState {
        !isSelectedNeededItem(neededText)
      }
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
        it.startsWith(ui.textArea.text) && UIUtil.getParentOfType(FindPopupPanel::class.java, ui) != null
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

    val replaceButtonText = FindBundle.message("find.replace.command")
    task {
      val replaceAllButtonText = FindBundle.message("find.popup.replace.all.button").dropMnemonic()
      text(LessonsBundle.message("find.in.files.press.replace.all", strong(replaceAllButtonText)))
      triggerByUiComponentAndHighlight { button: JButton ->
        button.text.isToStringContains(replaceAllButtonText)
      }
      triggerByUiComponentAndHighlight { button: JButton ->
        UIUtil.getParentOfType(JDialog::class.java, button)?.isModal == true && button.text.isToStringContains(replaceButtonText)
      }
      showWarningIfPopupClosed(true)
      test {
        ideFrame {
          button(replaceAllButtonText).click()
        }
      }
    }

    task {
      addFutureStep {
        (previous.ui as? JButton)?.addActionListener {
          completeStep()
        }
      }
      text(LessonsBundle.message("find.in.files.confirm.replace", strong(replaceButtonText)))
      restoreByUi()
      test(waitEditorToBeReady = false) {
        dialog(title = "Replace All") {
          button(replaceButtonText).click()
        }
      }
    }
  }

  private fun TaskRuntimeContext.isSelectedNeededItem(neededText: String): Boolean {
    return (previous.ui as? JTable)?.let {
      it.isShowing && it.selectedRow != -1 && it.selectedRow == it.findLastRowIndexOfItemWithText(neededText)
    } == true
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
    showWarning(LessonsBundle.message("find.in.files.popup.closed.warning.message", action(actionId), LessonUtil.actionName(actionId))) {
      getFindPopup()?.helper?.isReplaceState != isReplacePopup
    }
  }

  override val testScriptProperties = TaskTestContext.TestScriptProperties(10)

  override val suitableTips = listOf("FindReplaceToggle", "FindInPath")

  override val helpLinks: Map<String, String> get() = mapOf(
    Pair(LessonsBundle.message("find.in.files.help.link"),
         LessonUtil.getHelpLink("finding-and-replacing-text-in-project.html")),
  )
}

private fun resetFindSettings(project: Project) {
  FindManager.getInstance(project).findInProjectModel.apply {
    isWholeWordsOnly = false
    stringToFind = ""
    stringToReplace = ""
    directoryName = null
  }
  (FindInProjectSettings.getInstance(project) as? FindInProjectSettingsBase)
    ?.loadState(FindInProjectSettingsBase())
}