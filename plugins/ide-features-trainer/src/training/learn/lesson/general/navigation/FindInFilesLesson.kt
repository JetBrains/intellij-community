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
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.ui.UIUtil
import org.assertj.swing.core.MouseClickInfo
import org.assertj.swing.data.TableCell
import org.assertj.swing.fixture.JTableFixture
import org.assertj.swing.fixture.JTextComponentFixture
import org.intellij.lang.annotations.Language
import training.dsl.*
import training.dsl.LessonUtil.findLastRowIndexOfItemWithText
import training.learn.LessonsBundle
import training.learn.course.KLesson
import training.ui.LearningUiUtil.findComponentWithTimeout
import training.util.isToStringContains
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import java.util.*
import javax.swing.*

open class FindInFilesLesson(override val sampleFilePath: String,
                             private val helpUrl: String = "finding-and-replacing-text-in-project.html")
  : KLesson("Find in files", LessonsBundle.message("find.in.files.lesson.name")) {

  open val confirmReplaceAction: Boolean = true

  private lateinit var replaceTaskId: TaskContext.TaskId

  override val lessonContent: LessonContext.() -> Unit = {
    sdkConfigurationTasks()

    prepareRuntimeTask {
      resetFindSettings(project)
    }

    lateinit var showPopupTaskId: TaskContext.TaskId
    task("FindInPath") {
      showPopupTaskId = taskId
      text(LessonsBundle.message("find.in.files.show.find.popup",
                                 action(it), LessonUtil.actionName(it)))
      triggerUI().component { popup: FindPopupPanel ->
        !popup.helper.isReplaceState
      }
      test {
        actions(it)
      }
    }

    task {
      val appleText = "apple"
      text(LessonsBundle.message("find.in.files.type.to.find", code(appleText)))
      stateCheck { getFindPopup()?.stringToFind?.lowercase(Locale.getDefault()) == appleText }
      restoreByUi()
      test { type(appleText) }
    }

    task {
      val wholeWordsButtonText = FindBundle.message("find.whole.words").dropMnemonic()
      text(LessonsBundle.message("find.in.files.whole.words",
                                 code("apple"),
                                 code("pineapple"),
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
      triggerAndBorderHighlight().componentPart { table: JTable ->
        val rowIndex = findLastRowIndexOfItemWithText(table, neededText)
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
          val rowIndex = { findLastRowIndexOfItemWithText(table, neededText) }
          tableFixture.pointAt(TableCell.row(rowIndex()).column(0)) // It seems, the list may change for a while
          tableFixture.click(TableCell.row(rowIndex()).column(0), MouseClickInfo.leftButton())
        }
      }
    }

    task {
      text(LessonsBundle.message("find.in.files.go.to.file", LessonUtil.rawEnter()))
      addFutureStep {
        project.messageBus.connect(taskDisposable).subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, object : FileEditorManagerListener {
          override fun fileOpened(source: FileEditorManager, file: VirtualFile) {
            if (file.name != sampleFilePath.substringAfterLast('/')) {
              completeStep()
            }
          }
        })
      }
      restoreState(delayMillis = defaultRestoreDelay) {
        !isSelectedNeededItem(neededText)
      }
      test { invokeActionViaShortcut("ENTER") }
    }

    task("ReplaceInPath") {
      text(LessonsBundle.message("find.in.files.show.replace.popup",
                                 action(it), LessonUtil.actionName(it)))
      triggerUI().component { popup: FindPopupPanel ->
        popup.helper.isReplaceState
      }
      test { actions(it) }
    }

    task {
      val orangeText = "orange"
      text(LessonsBundle.message("find.in.files.type.to.replace",
                                 code("apple"), code(orangeText)))
      triggerAndBorderHighlight().component { ui: SearchTextArea ->
        orangeText.startsWith(ui.textArea.text) && UIUtil.getParentOfType(FindPopupPanel::class.java, ui) != null
      }
      stateCheck {
        getFindPopup()?.helper?.model?.let { model ->
          model.stringToReplace == orangeText && model.stringToFind == "apple"
        } ?: false
      }
      restoreByUi()
      test {
        ideFrame {
          val textArea = findComponentWithTimeout { textArea: JTextArea -> textArea.text == "" }
          JTextComponentFixture(robot(), textArea).click()
          type(orangeText)
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
    val replaceAllButtonText = FindBundle.message("find.popup.replace.all.button").dropMnemonic()
    task {
      replaceTaskId = taskId
      text(LessonsBundle.message("find.in.files.press.replace.all", strong(replaceAllButtonText)))
      triggerAndFullHighlight().component { button: JButton ->
        button.text.isToStringContains(replaceAllButtonText)
      }
      if (confirmReplaceAction) {
        triggerAndFullHighlight().component { button: JButton ->
          UIUtil.getParentOfType(JDialog::class.java, button)?.isModal == true && button.text.isToStringContains(replaceButtonText)
        }
      }
      showWarningIfPopupClosed(true)
      test {
        ideFrame {
          button(replaceAllButtonText).click()
        }
      }
    }

    if (confirmReplaceAction) {
      task {
        addFutureStep {
          (previous.ui as? JButton)?.addActionListener {
            completeStep()
          }
        }
        text(LessonsBundle.message("find.in.files.confirm.replace", strong(replaceButtonText)))
        restoreByUi()
        test(waitEditorToBeReady = false) {
          dialog(title = replaceAllButtonText) {
            button(replaceButtonText).click()
          }
        }
      }
    }

    task {
      restoreByUi(taskId)
      stateCheck { editor.document.text.contains("orange") }
    }
  }

  private fun TaskRuntimeContext.isSelectedNeededItem(neededText: String): Boolean {
    return (previous.ui as? JTable)?.let {
      it.isShowing && it.selectedRow != -1 && it.selectedRow == findLastRowIndexOfItemWithText(it,neededText)
    } == true
  }

  private fun TaskRuntimeContext.getFindPopup(): FindPopupPanel? {
    return UIUtil.getParentOfType(FindPopupPanel::class.java, focusOwner)
  }

  private fun TaskContext.highlightAndTriggerWhenButtonSelected(buttonText: String) {
    triggerAndFullHighlight().component { button: ActionButton ->
      button.action.templateText == buttonText
    }
    triggerUI().component { button: ActionButton ->
      button.action.templateText == buttonText && button.isSelected
    }
  }

  private fun TaskContext.showWarningIfPopupClosed(isReplacePopup: Boolean) {
    @Language("devkit-action-id") val actionId = if (isReplacePopup) "ReplaceInPath" else "FindInPath"
    showWarning(LessonsBundle.message("find.in.files.popup.closed.warning.message", action(actionId), LessonUtil.actionName(actionId))) {
      getFindPopup()?.helper?.isReplaceState != isReplacePopup
    }
  }

  override val testScriptProperties = TaskTestContext.TestScriptProperties(10)

  override val helpLinks: Map<String, String>
    get() = mapOf(
      Pair(LessonsBundle.message("find.in.files.help.link"),
           LessonUtil.getHelpLink(helpUrl)),
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