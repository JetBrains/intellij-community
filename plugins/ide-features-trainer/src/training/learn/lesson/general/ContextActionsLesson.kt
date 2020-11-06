// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package training.learn.lesson.general

import com.intellij.codeInsight.intention.impl.ShowIntentionActionsHandler
import training.commands.kotlin.TaskRuntimeContext
import training.learn.LessonsBundle
import training.learn.interfaces.Module
import training.learn.lesson.kimpl.*

abstract class ContextActionsLesson(module: Module, lang: String) :
  KLesson("context.actions", LessonsBundle.message("context.actions.lesson.name"), module, lang) {

  abstract val sample: LessonSample
  abstract val warningQuickFix: String
  abstract val warningCaret: String

  abstract val generalIntention: String
  abstract val generalIntentionCaret: String

  override val lessonContent: LessonContext.() -> Unit = {
    prepareSample(sample)
    caret(warningCaret)
    task("ShowIntentionActions") {
      text(LessonsBundle.message("context.actions.invoke.intentions.for.warning", LessonUtil.actionName(it), action(it)))
      triggerByListItemAndHighlight(highlightBorder = true, highlightInside = false) { item ->
        item.toString().contains(warningQuickFix)
      }
    }

    var before = ""
    fun TaskRuntimeContext.updateBefore() {
      before = editor.document.text
    }

    prepareRuntimeTask {
      updateBefore()
    }

    task {
      text(LessonsBundle.message("context.actions.fix.warning", strong(warningQuickFix)))
      stateCheck {
        (insideIntention() && before != editor.document.text).also { updateBefore() }
      }
    }

    caret(generalIntentionCaret)
    task("ShowIntentionActions") {
      text(LessonsBundle.message("context.actions.invoke.general.intentions", LessonUtil.actionName(it), action(it)))
      triggerByListItemAndHighlight(highlightBorder = true, highlightInside = false) { item ->
        item.toString().contains(generalIntention)
      }
    }

    prepareRuntimeTask {
      before = editor.document.text
    }

    task {
      text(LessonsBundle.message("context.actions.apply.intention", strong(generalIntention)))
      stateCheck {
        (insideIntention() && before != editor.document.text).also { updateBefore() }
      }
    }

    text(LessonsBundle.message("context.actions.refactorings.promotion",
                               LessonUtil.actionName("ShowIntentionActions"),
                               strong(LessonsBundle.message("refactorings.module.name"))))

    firstLessonCompletedMessage()
  }

  private fun insideIntention() = Thread.currentThread().stackTrace.any {
    it.className.contains(ShowIntentionActionsHandler::class.simpleName!!)
  }
}
