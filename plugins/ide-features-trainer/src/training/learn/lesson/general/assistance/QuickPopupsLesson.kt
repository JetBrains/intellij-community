// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package training.learn.lesson.general.assistance

import com.intellij.codeInsight.documentation.DocumentationEditorPane
import com.intellij.codeInsight.documentation.DocumentationHintEditorPane
import com.intellij.codeInsight.hint.ImplementationViewComponent
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.KeyboardShortcut
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.toolWindow.InternalDecoratorImpl
import com.intellij.util.ui.UIUtil
import org.assertj.swing.timing.Timeout
import training.dsl.LessonContext
import training.dsl.LessonSample
import training.dsl.LessonUtil
import training.dsl.LessonUtil.restoreIfModifiedOrMoved
import training.dsl.TaskTestContext
import training.dsl.triggerOnQuickDocumentationPopup
import training.learn.LessonsBundle
import training.learn.course.KLesson
import training.ui.LearningUiUtil
import java.util.concurrent.TimeUnit

class QuickPopupsLesson(
  private val sample: LessonSample,
  private val helpUrl: String = "using-code-editor.html#quick_popups",
)
  : KLesson("CodeAssistance.QuickPopups", LessonsBundle.message("quick.popups.lesson.name")) {

  override val lessonContent: LessonContext.() -> Unit = {
    prepareSample(sample)

    task("QuickJavaDoc") {
      text(LessonsBundle.message("quick.popups.show.documentation", strong("Quick Documentation"), action(it)))
      triggerOnQuickDocumentationPopup()
      restoreIfModifiedOrMoved(sample)
      test { actions(it) }
    }

    waitBeforeContinue(200)

    task("QuickJavaDoc") {
      text(LessonsBundle.message("quick.popups.press.show.documentation.again", action(it)))
      triggerUI().component { pane: DocumentationEditorPane ->
        UIUtil.getParentOfType(InternalDecoratorImpl::class.java, pane) != null
      }
      restoreIfModifiedOrMoved(sample)
      test { pressShortcutInDocumentationPopup(it) }
    }

    task("QuickJavaDoc") {
      text(LessonsBundle.message("quick.popups.press.show.documentation.focus", action(it)))
      stateCheck {
        focusOwner is DocumentationHintEditorPane
      }
      restoreIfModifiedOrMoved(sample)
      test { actions(it) }
    }

    task("CloseContent") {
      text(LessonsBundle.message("quick.popups.close", action(it)))
      stateCheck {
        previous.ui?.isShowing != true
      }
      trigger(it)
      restoreIfModifiedOrMoved()
      test { actions(it) }
    }

    task("QuickImplementations") {
      text(LessonsBundle.message("quick.popups.show.implementation", action(it)))
      triggerUI().component { _: ImplementationViewComponent -> true }
      restoreIfModifiedOrMoved()
      test {
        actions(it)
        val delay = Timeout.timeout(3, TimeUnit.SECONDS)
        LearningUiUtil.findShowingComponentWithTimeout(project, ImplementationViewComponent::class.java, delay)
        Thread.sleep(500)
        invokeActionViaShortcut("ESCAPE")
      }
    }
  }

  /**
   * The documentation popup opens the tool window only by its own action, which has the [actionId] shortcut.
   * The global [actionId] action only focuses the popup, so press the shortcut in the focused popup.
   */
  private fun TaskTestContext.pressShortcutInDocumentationPopup(actionId: String) {
    val deadline = System.currentTimeMillis() + POPUP_FOCUS_TIMEOUT_MS
    while (!isDocumentationPopupFocused() && System.currentTimeMillis() < deadline) {
      Thread.sleep(50)
    }
    val keyStroke = ActionManager.getInstance().getAction(actionId).shortcutSet.shortcuts
      .filterIsInstance<KeyboardShortcut>()
      .firstOrNull { it.secondKeyStroke == null }
      ?.firstKeyStroke
    if (keyStroke == null) {
      actions(actionId)
      return
    }
    robot.pressAndReleaseKey(keyStroke.keyCode, keyStroke.modifiers)
  }

  private fun TaskTestContext.isDocumentationPopupFocused(): Boolean = invokeAndWaitIfNeeded {
    val focusOwner = IdeFocusManager.getInstance(project).focusOwner
    focusOwner is DocumentationHintEditorPane && UIUtil.getParentOfType(InternalDecoratorImpl::class.java, focusOwner) == null
  }

  override val helpLinks: Map<String, String>
    get() = mapOf(
      Pair(LessonsBundle.message("quick.popups.help.link"),
           LessonUtil.getHelpLink(helpUrl)),
    )
}

private const val POPUP_FOCUS_TIMEOUT_MS = 3000L