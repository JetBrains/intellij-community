// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package training.learn.lesson.general.navigation

import com.intellij.ide.actions.searcheverywhere.SearchEverywhereManagerImpl
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereUI
import com.intellij.openapi.actionSystem.impl.ActionButtonWithText
import com.intellij.openapi.editor.impl.EditorComponentImpl
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiNameIdentifierOwner
import com.intellij.psi.search.EverythingGlobalScope
import com.intellij.psi.search.ProjectScope
import com.intellij.ui.components.fields.ExtendableTextField
import com.intellij.util.ui.UIUtil
import training.dsl.*
import training.dsl.LessonUtil.adjustPopupPosition
import training.dsl.LessonUtil.restorePopupPosition
import training.learn.LessonsBundle
import training.learn.course.KLesson
import training.learn.course.LessonType
import training.util.LessonEndInfo
import training.util.isToStringContains
import java.awt.Point
import java.awt.event.KeyEvent
import javax.swing.JList

abstract class SearchEverywhereLesson : KLesson("Search everywhere", LessonsBundle.message("search.everywhere.lesson.name")) {
  abstract override val existedFile: String?

  abstract val resultFileName: String

  override val lessonType: LessonType = LessonType.PROJECT

  private val requiredClassName = "QuadraticEquationsSolver"

  private var backupPopupLocation: Point? = null

  override val lessonContent: LessonContext.() -> Unit = {
    task("SearchEverywhere") {
      triggerByUiComponentAndHighlight(highlightInside = false) { ui: ExtendableTextField ->
        UIUtil.getParentOfType(SearchEverywhereUI::class.java, ui) != null
      }
      text(LessonsBundle.message("search.everywhere.invoke.search.everywhere", LessonUtil.actionName(it),
                                 LessonUtil.rawKeyStroke(KeyEvent.VK_SHIFT)))
      test { actions(it) }
    }

    task("que") {
      before {
        if (backupPopupLocation == null) {
          backupPopupLocation = adjustPopupPosition(SearchEverywhereManagerImpl.LOCATION_SETTINGS_KEY)
        }
      }
      text(LessonsBundle.message("search.everywhere.type.prefixes", strong("quadratic"), strong("equation"), code(it)))
      stateCheck { checkWordInSearch(it) }
      restoreByUi()
      test {
        Thread.sleep(500)
        type(it)
      }
    }

    task {
      triggerByListItemAndHighlight { item ->
        if (item is PsiNameIdentifierOwner)
          item.name == requiredClassName
        else item.isToStringContains(requiredClassName)
      }
      restoreByUi()
    }

    task {
      text(LessonsBundle.message("search.everywhere.navigate.to.class", code(requiredClassName), LessonUtil.rawEnter()))
      stateCheck {
        FileEditorManager.getInstance(project).selectedEditor?.file?.name.equals(resultFileName)
      }
      restoreByUi()
      test {
        Thread.sleep(500) // wait items loading
        val jList = previous.ui as? JList<*> ?: error("No list")
        val itemIndex = LessonUtil.findItem(jList) { item ->
          if (item is PsiNameIdentifierOwner)
            item.name == requiredClassName
          else item.isToStringContains(requiredClassName)
        } ?: error("No item")

        ideFrame {
          jListFixture(jList).clickItem(itemIndex)
        }
      }
    }

    actionTask("GotoClass") {
      LessonsBundle.message("search.everywhere.goto.class", action(it))
    }

    task("bufre") {
      text(LessonsBundle.message("search.everywhere.type.class.name", code(it)))
      stateCheck { checkWordInSearch(it) }
      restoreAfterStateBecomeFalse { !checkInsideSearchEverywhere() }
      test { type(it) }
    }

    task(EverythingGlobalScope.getNameText()) {
      text(LessonsBundle.message("search.everywhere.use.all.places",
                                 strong(ProjectScope.getProjectFilesScopeName()), strong(it)))
      triggerByUiComponentAndHighlight { _: ActionButtonWithText -> true }
      triggerByUiComponentAndHighlight(false, false) { button: ActionButtonWithText ->
        button.accessibleContext.accessibleName == it
      }
      showWarning(LessonsBundle.message("search.everywhere.class.popup.closed.warning.message", action("GotoClass"))) {
        !checkInsideSearchEverywhere() && focusOwner !is JList<*>
      }
      test {
        invokeActionViaShortcut("ALT P")
      }
    }

    task("QuickJavaDoc") {
      text(LessonsBundle.message("search.everywhere.quick.documentation", action(it)))
      triggerOnQuickDocumentationPopup()
      restoreByUi()
      test { actions(it) }
    }

    task {
      text(LessonsBundle.message("search.everywhere.close.documentation.popup", LessonUtil.rawKeyStroke(KeyEvent.VK_ESCAPE)))
      stateCheck { previous.ui?.isShowing != true }
      test { invokeActionViaShortcut("ENTER") }
    }

    task {
      text(LessonsBundle.message("search.everywhere.finish", action("GotoSymbol"), action("GotoFile")))
    }

    if (TaskTestContext.inTestMode) task {
      stateCheck { focusOwner is EditorComponentImpl }
      test {
        invokeActionViaShortcut("ESCAPE")
        invokeActionViaShortcut("ESCAPE")
      }
    }

    epilogue()
  }

  override fun onLessonEnd(project: Project, lessonEndInfo: LessonEndInfo) {
    restorePopupPosition(project, SearchEverywhereManagerImpl.LOCATION_SETTINGS_KEY, backupPopupLocation)
    backupPopupLocation = null
  }

  open fun LessonContext.epilogue() = Unit

  private fun TaskRuntimeContext.checkWordInSearch(expected: String): Boolean =
    (focusOwner as? ExtendableTextField)?.text?.equals(expected, ignoreCase = true) == true

  private fun TaskRuntimeContext.checkInsideSearchEverywhere(): Boolean {
    return UIUtil.getParentOfType(SearchEverywhereUI::class.java, focusOwner) != null
  }

  override val suitableTips = listOf("SearchEverywhere", "GoToClass", "search_everywhere_general")

  override val helpLinks: Map<String, String> get() = mapOf(
    Pair(LessonsBundle.message("help.search.everywhere"),
         LessonUtil.getHelpLink("searching-everywhere.html")),
  )
}