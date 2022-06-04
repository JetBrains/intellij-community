// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package training.learn.lesson.general.completion

import org.jetbrains.annotations.Nls
import training.dsl.LearningDslBase
import training.dsl.LessonContext
import training.dsl.LessonSample
import training.dsl.LessonUtil
import training.dsl.LessonUtil.checkExpectedStateOfEditor
import training.learn.LessonsBundle
import training.learn.course.KLesson
import training.util.isToStringContains
import java.util.regex.Pattern

abstract class PostfixCompletionLesson : KLesson("Postfix completion", LessonsBundle.message("postfix.completion.lesson.name")) {
  protected abstract val sample: LessonSample
  protected abstract val result: String

  protected abstract val completionSuffix: String
  protected abstract val completionItem: String

  protected abstract fun LearningDslBase.getTypeTaskText(): @Nls String
  protected abstract fun LearningDslBase.getCompleteTaskText(): @Nls String

  override val lessonContent: LessonContext.() -> Unit = {
    prepareSample(sample)

    task {
      text(LessonsBundle.message("postfix.completion.intro") + " " + getTypeTaskText())
      triggerAndBorderHighlight().listItem {
        it.isToStringContains(completionItem)
      }
      proposeRestore {
        checkExpectedStateOfEditor(sample) { completionSuffix.startsWith(it) }
      }
      test {
        type(completionSuffix)
      }
    }

    task {
      text(getCompleteTaskText())
      stateCheck { editor.document.text == result }
      restoreByUi()
      test(waitEditorToBeReady = false) {
        ideFrame {
          jList(completionItem).item(Pattern.compile(completionItem)).doubleClick()
        }
      }
    }
  }

  override val suitableTips = listOf("PostfixCompletion")

  override val helpLinks: Map<String, String> get() = mapOf(
    Pair(LessonsBundle.message("postfix.completion.help.link"),
         LessonUtil.getHelpLink("auto-completing-code.html#postfix_completion")),
  )
}