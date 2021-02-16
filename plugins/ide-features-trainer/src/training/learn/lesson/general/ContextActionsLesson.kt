// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package training.learn.lesson.general

import com.intellij.codeInsight.intention.impl.ShowIntentionActionsHandler
import com.intellij.openapi.editor.impl.EditorComponentImpl
import training.dsl.*
import training.dsl.LessonUtil.restoreIfModifiedOrMovedIncorrectly
import training.learn.LessonsBundle
import training.learn.course.KLesson

abstract class ContextActionsLesson : KLesson("context.actions", LessonsBundle.message("context.actions.lesson.name")) {
  abstract val sample: LessonSample
  abstract val warningQuickFix: String
  abstract val warningPossibleArea: String

  abstract val intentionText: String
  abstract val intentionCaret: String
  abstract val intentionPossibleArea: String

  override val testScriptProperties = TaskTestContext.TestScriptProperties(skipTesting = true)

  override val lessonContent: LessonContext.() -> Unit = {
    prepareSample(sample)
    lateinit var showIntentionsTaskId: TaskContext.TaskId
    task("ShowIntentionActions") {
      showIntentionsTaskId = taskId
      text(LessonsBundle.message("context.actions.invoke.intentions.for.warning", LessonUtil.actionName(it), action(it)))
      triggerByListItemAndHighlight(highlightBorder = true, highlightInside = false) { item ->
        item.toString().contains(warningQuickFix)
      }
      restoreIfModifiedOrMovedIncorrectly(warningPossibleArea)
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
      restoreIfIntentionsPopupClosed(showIntentionsTaskId)
    }

    caret(intentionCaret)
    task("ShowIntentionActions") {
      showIntentionsTaskId = taskId
      text(LessonsBundle.message("context.actions.invoke.general.intentions", LessonUtil.actionName(it), action(it)))
      triggerByListItemAndHighlight(highlightBorder = true, highlightInside = false) { item ->
        item.toString().contains(intentionText)
      }
      restoreIfModifiedOrMovedIncorrectly(intentionPossibleArea)
    }

    prepareRuntimeTask {
      before = editor.document.text
    }

    task {
      text(LessonsBundle.message("context.actions.apply.intention", strong(intentionText)))
      stateCheck {
        (insideIntention() && before != editor.document.text).also { updateBefore() }
      }
      restoreIfIntentionsPopupClosed(showIntentionsTaskId)
    }

    text(LessonsBundle.message("context.actions.refactorings.promotion",
                               LessonUtil.actionName("ShowIntentionActions"),
                               strong(LessonsBundle.message("refactorings.module.name"))))

    firstLessonCompletedMessage()
  }

  private fun insideIntention() = Thread.currentThread().stackTrace.any {
    it.className.contains(ShowIntentionActionsHandler::class.simpleName!!)
  }

  private fun TaskContext.restoreIfIntentionsPopupClosed(restoreId: TaskContext.TaskId) {
    restoreState(restoreId = restoreId, delayMillis = defaultRestoreDelay) {
      focusOwner is EditorComponentImpl
    }
  }
}
