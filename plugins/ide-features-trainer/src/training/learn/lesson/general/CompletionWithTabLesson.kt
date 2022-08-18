// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package training.learn.lesson.general

import training.dsl.LessonContext
import training.dsl.LessonSample
import training.dsl.LessonUtil
import training.dsl.LessonUtil.restoreIfModifiedOrMoved
import training.learn.LessonsBundle
import training.learn.course.KLesson
import training.util.isToStringContains

abstract class CompletionWithTabLesson(private val proposal: String) :
  KLesson("Completion with tab", LessonsBundle.message("completion.with.tab.lesson.name")) {

  abstract val sample: LessonSample

  override val lessonContent: LessonContext.() -> Unit
    get() = {
      prepareSample(sample)

      task("CodeCompletion") {
        text(LessonsBundle.message("completion.with.tab.begin.completion", action(it), code(proposal)))
        triggerAndBorderHighlight().listItem { item -> item.isToStringContains(proposal) }
        restoreIfModifiedOrMoved(sample)
        test { actions(it) }
      }

      actionTask("EditorChooseLookupItemReplace") {
        restoreByUi()
        LessonsBundle.message("completion.with.tab.finish.with.tab", code(proposal), action("EditorTab"))
      }
    }

  override val suitableTips = listOf("TabInLookups")

  override val helpLinks: Map<String, String> get() = mapOf(
    Pair(LessonsBundle.message("help.code.completion"),
         LessonUtil.getHelpLink("auto-completing-code.html")),
  )
}
