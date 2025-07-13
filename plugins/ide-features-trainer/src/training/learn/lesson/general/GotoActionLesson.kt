// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package training.learn.lesson.general

import com.intellij.ide.IdeBundle
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereUI
import com.intellij.ide.util.gotoByName.GotoActionModel
import com.intellij.idea.ActionsBundle
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.client.ClientSystemInfo
import com.intellij.openapi.editor.actions.ToggleShowLineNumbersGloballyAction
import com.intellij.openapi.editor.impl.EditorComponentImpl
import training.dsl.*
import training.dsl.EditorSettingsState.isLineNumbersShown
import training.dsl.LessonUtil.checkInsideSearchEverywhere
import training.dsl.LessonUtil.showWarningIfSearchPopupClosed
import training.learn.LearnBundle
import training.learn.LessonsBundle
import training.learn.course.KLesson
import training.util.isToStringContains
import java.awt.event.KeyEvent
import java.util.Locale
import javax.swing.JDialog

class GotoActionLesson(private val sample: LessonSample,
                       private val firstLesson: Boolean = false,
                       private val helpUrl: String = "searching-everywhere.html#search_actions")
  : KLesson("Actions", LessonsBundle.message("goto.action.lesson.name")) {

  companion object {
    private const val FIND_ACTION_WORKAROUND: String = "https://intellij-support.jetbrains.com/hc/en-us/articles/360005137400-Cmd-Shift-A-hotkey-opens-Terminal-with-apropos-search-instead-of-the-Find-Action-dialog"
  }

  override val lessonContent: LessonContext.() -> Unit
    get() = {
      prepareSample(sample)
      task("GotoAction") {
        text(LessonsBundle.message("goto.action.use.find.action.1",
                                   LessonUtil.actionName(it), action(it)))

        if (ClientSystemInfo.isMac()) {
          text(LessonsBundle.message("goto.action.mac.workaround", LessonUtil.actionName(it), FIND_ACTION_WORKAROUND))
        }

        text(LessonsBundle.message("goto.action.use.find.action.2",
                                   LessonUtil.actionName("SearchEverywhere"), LessonUtil.rawKeyStroke(KeyEvent.VK_SHIFT)))

        stateCheck { checkInsideSearchEverywhere() }
        test { actions(it) }
      }

      task("About") {
        showWarningIfSearchPopupClosed()
        text(LessonsBundle.message("goto.action.invoke.about.action",
                                   LessonUtil.actionName(it).lowercase(Locale.getDefault()), LessonUtil.rawEnter()))
        triggerUI().component { dialog: JDialog ->
          dialog.title.isToStringContains(IdeBundle.message("about.popup.about.app", ApplicationNamesInfo.getInstance().fullProductName))
        }
        test { actions(it) }
      }

      task {
        text(LessonsBundle.message("goto.action.to.return.to.the.editor", action("EditorEscape")))
        stateCheck {
          focusOwner is EditorComponentImpl
        }
        test(waitEditorToBeReady = false) {
          invokeActionViaShortcut("ESCAPE")
        }
      }

      task("GotoAction") {
        text(LessonsBundle.message("goto.action.invoke.again",
                                   action(it), LessonUtil.rawKeyStroke(KeyEvent.VK_SHIFT)))
        stateCheck { checkInsideSearchEverywhere() }
        test { actions(it) }
      }

      val showLineNumbersName = ActionsBundle.message("action.EditorGutterToggleGlobalLineNumbers.text")
      task {
        val prefix = LearnBundle.message("show.line.number.prefix.to.show.first")
        text(LessonsBundle.message("goto.action.show.line.numbers.request", strong(prefix), strong(showLineNumbersName)))
        triggerAndBorderHighlight().listItem { item ->
          val matchedValue = item as? GotoActionModel.MatchedValue
          val actionWrapper = matchedValue?.value as? GotoActionModel.ActionWrapper
          val action = actionWrapper?.action
          action is ToggleShowLineNumbersGloballyAction
        }
        restoreState { !checkInsideSearchEverywhere() }
        test {
          waitComponent(SearchEverywhereUI::class.java)
          type(prefix)
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

  override val helpLinks: Map<String, String> get() = mapOf(
    Pair(LessonsBundle.message("help.search.everywhere"),
         LessonUtil.getHelpLink(helpUrl)),
  )
}