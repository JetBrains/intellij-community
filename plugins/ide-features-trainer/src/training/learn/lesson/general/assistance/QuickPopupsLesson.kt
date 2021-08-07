// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package training.learn.lesson.general.assistance

import com.intellij.codeInsight.documentation.DocumentationComponent
import com.intellij.codeInsight.documentation.QuickDocUtil
import com.intellij.codeInsight.hint.ImplementationViewComponent
import org.fest.swing.timing.Timeout
import training.dsl.LessonContext
import training.dsl.LessonSample
import training.dsl.LessonUtil.restoreIfModifiedOrMoved
import training.dsl.TaskRuntimeContext
import training.learn.LessonsBundle
import training.learn.course.KLesson
import training.ui.LearningUiUtil
import java.util.concurrent.TimeUnit

class QuickPopupsLesson(private val sample: LessonSample) :
  KLesson("CodeAssistance.QuickPopups", LessonsBundle.message("quick.popups.lesson.name")) {

  override val lessonContent: LessonContext.() -> Unit = {
    prepareSample(sample)

    task("QuickJavaDoc") {
      text(LessonsBundle.message("quick.popups.show.documentation", action(it)))
      triggerByUiComponentAndHighlight(highlightBorder = false, highlightInside = false) { _: DocumentationComponent -> true }
      restoreIfModifiedOrMoved()
      test { actions(it) }
    }

    task {
      text(LessonsBundle.message("quick.popups.press.escape", action("EditorEscape")))
      stateCheck { checkDocComponentClosed() }
      restoreIfModifiedOrMoved()
      test {
        invokeActionViaShortcut("ESCAPE")
      }
    }

    task("QuickImplementations") {
      text(LessonsBundle.message("quick.popups.show.implementation", action(it)))
      triggerByUiComponentAndHighlight(highlightBorder = false, highlightInside = false) { _: ImplementationViewComponent -> true }
      restoreIfModifiedOrMoved()
      test {
        actions(it)
        val delay = Timeout.timeout(3, TimeUnit.SECONDS)
        LearningUiUtil.findShowingComponentWithTimeout(null, ImplementationViewComponent::class.java, delay)
        invokeActionViaShortcut("ESCAPE")
      }
    }
  }

  private fun TaskRuntimeContext.checkDocComponentClosed(): Boolean {
    val activeDocComponent = QuickDocUtil.getActiveDocComponent(project)
    return activeDocComponent == null || !activeDocComponent.isShowing
  }
}