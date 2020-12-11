// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package training.learn.lesson.general

import training.learn.LessonsBundle
import training.learn.interfaces.Module
import training.learn.lesson.kimpl.KLesson
import training.learn.lesson.kimpl.LessonContext
import training.learn.lesson.kimpl.LessonSample
import training.learn.lesson.kimpl.LessonUtil.restoreIfModifiedOrMoved

abstract class CompletionWithTabLesson(module: Module, lang: String, private val proposal: String) :
  KLesson("Completion with tab", LessonsBundle.message("completion.with.tab.lesson.name"), module, lang) {

  abstract val sample: LessonSample

  override val lessonContent: LessonContext.() -> Unit
    get() = {
      prepareSample(sample)

      task("CodeCompletion") {
        text(LessonsBundle.message("completion.with.tab.begin.completion", action(it)))
        triggerByListItemAndHighlight { item -> item.toString() == proposal }
        restoreIfModifiedOrMoved()
        test { actions(it) }
      }

      actionTask("EditorChooseLookupItemReplace") {
        restoreByUi()
        LessonsBundle.message("completion.with.tab.finish.with.tab", code(proposal), action("EditorTab"))
      }
    }
}
