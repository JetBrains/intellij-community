// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package training.learn.lesson.general.assistance

import com.intellij.codeInsight.documentation.DocumentationEditorPane
import com.intellij.codeInsight.documentation.DocumentationHintEditorPane
import com.intellij.codeInsight.hint.ImplementationViewComponent
import com.intellij.toolWindow.InternalDecoratorImpl
import com.intellij.util.ui.UIUtil
import org.assertj.swing.timing.Timeout
import training.dsl.*
import training.dsl.LessonUtil.restoreIfModifiedOrMoved
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
      test { actions(it) }
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
      test { action(it) }
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

  override val helpLinks: Map<String, String>
    get() = mapOf(
      Pair(LessonsBundle.message("quick.popups.help.link"),
           LessonUtil.getHelpLink(helpUrl)),
    )
}