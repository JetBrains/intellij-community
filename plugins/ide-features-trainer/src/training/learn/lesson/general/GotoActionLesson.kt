// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package training.learn.lesson.general

import com.intellij.ide.actions.AboutPopup
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereUI
import com.intellij.ide.util.gotoByName.GotoActionModel
import com.intellij.idea.ActionsBundle
import com.intellij.openapi.editor.actions.ToggleShowLineNumbersGloballyAction
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable
import com.intellij.openapi.editor.impl.EditorComponentImpl
import com.intellij.openapi.util.SystemInfo
import com.intellij.util.ui.UIUtil
import org.fest.swing.driver.ComponentDriver
import training.dsl.*
import training.learn.LearnBundle
import training.learn.LessonsBundle
import training.learn.course.KLesson
import java.awt.Component
import java.awt.event.KeyEvent
import javax.swing.JPanel

class GotoActionLesson(private val sample: LessonSample, private val firstLesson: Boolean = false) :
  KLesson("Actions", LessonsBundle.message("goto.action.lesson.name")) {

  companion object {
    private const val FIND_ACTION_WORKAROUND: String = "https://intellij-support.jetbrains.com/hc/en-us/articles/360005137400-Cmd-Shift-A-hotkey-opens-Terminal-with-apropos-search-instead-of-the-Find-Action-dialog"
  }

  override val lessonContent: LessonContext.() -> Unit
    get() = {
      prepareSample(sample)
      task("GotoAction") {
        text(LessonsBundle.message("goto.action.use.find.action.1",
                                   LessonUtil.actionName(it), action(it)))

        if (SystemInfo.isMacOSMojave) {
          text(LessonsBundle.message("goto.action.mac.workaround", LessonUtil.actionName(it), FIND_ACTION_WORKAROUND))
        }

        text(LessonsBundle.message("goto.action.use.find.action.2",
                                   LessonUtil.actionName("SearchEverywhere"), LessonUtil.rawKeyStroke(KeyEvent.VK_SHIFT)))

        stateCheck { checkInsideSearchEverywhere() }
        test { actions(it) }
      }

      actionTask("About") {
        showWarningIfSearchPopupClosed()
        LessonsBundle.message("goto.action.invoke.about.action",
                              LessonUtil.actionName(it).toLowerCase(), LessonUtil.rawEnter())
      }

      task {
        text(LessonsBundle.message("goto.action.to.return.to.the.editor", action("EditorEscape")))
        var aboutHasBeenFocused = false
        stateCheck {
          aboutHasBeenFocused = aboutHasBeenFocused || focusOwner is AboutPopup.PopupPanel
          aboutHasBeenFocused && focusOwner is EditorComponentImpl
        }
        test {
          ideFrame {
            waitComponent(JPanel::class.java, "InfoSurface")
            // Note 1: it is editor from test IDE fixture
            // Note 2: In order to pass this task without interference with later task I need to firstly focus lesson
            // and only then press Escape
            ComponentDriver<Component>(robot).focusAndWaitForFocusGain(editor.contentComponent)
            invokeActionViaShortcut("ESCAPE")
          }
        }
      }

      task("GotoAction") {
        text(LessonsBundle.message("goto.action.invoke.again",
                                   action(it), LessonUtil.rawKeyStroke(KeyEvent.VK_SHIFT)))
        stateCheck { checkInsideSearchEverywhere() }
        test { actions(it) }
      }

      val showLineNumbersName = ActionsBundle.message("action.EditorGutterToggleGlobalLineNumbers.text")
      task(LearnBundle.message("show.line.number.prefix.to.show.first")) {
        text(LessonsBundle.message("goto.action.show.line.numbers.request", strong(it), strong(showLineNumbersName)))
        triggerByListItemAndHighlight { item ->
          val matchedValue = item as? GotoActionModel.MatchedValue
          val actionWrapper = matchedValue?.value as? GotoActionModel.ActionWrapper
          val action = actionWrapper?.action
          action is ToggleShowLineNumbersGloballyAction
        }
        restoreState { !checkInsideSearchEverywhere() }
        test {
          waitComponent(SearchEverywhereUI::class.java)
          type(it)
        }
      }

      val lineNumbersShown = isLineNumbersShown()
      task {
        text(LessonsBundle.message("goto.action.first.lines.toggle", if (lineNumbersShown) 0 else 1))
        stateCheck { isLineNumbersShown() == !lineNumbersShown }
        showWarningIfSearchPopupClosed()
        test {
          ideFrame {
            jList(showLineNumbersName).item(showLineNumbersName).click()
          }
        }
      }

      task {
        text(LessonsBundle.message("goto.action.second.lines.toggle", if (lineNumbersShown) 0 else 1))
        stateCheck { isLineNumbersShown() == lineNumbersShown }
        showWarningIfSearchPopupClosed()
        test {
          ideFrame {
            jList(showLineNumbersName).item(showLineNumbersName).click()
          }
        }
      }

      if (firstLesson) {
        firstLessonCompletedMessage()
      }
    }

  private fun TaskRuntimeContext.checkInsideSearchEverywhere(): Boolean {
    return UIUtil.getParentOfType(SearchEverywhereUI::class.java, focusOwner) != null
  }

  private fun TaskContext.showWarningIfSearchPopupClosed() {
    showWarning(LessonsBundle.message("goto.action.popup.closed.warning.message", action("GotoAction"),
                                      LessonUtil.rawKeyStroke(KeyEvent.VK_SHIFT))) {
      !checkInsideSearchEverywhere()
    }
  }

  private fun isLineNumbersShown() = EditorSettingsExternalizable.getInstance().isLineNumbersShown
}