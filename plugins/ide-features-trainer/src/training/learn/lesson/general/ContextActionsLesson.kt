// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package training.learn.lesson.general

import com.intellij.codeInsight.intention.impl.ShowIntentionActionsHandler
import com.intellij.openapi.editor.impl.EditorComponentImpl
import training.dsl.*
import training.dsl.LessonUtil.restoreIfModifiedOrMovedIncorrectly
import training.learn.LessonsBundle
import training.learn.course.KLesson
import training.util.isToStringContains
import java.util.concurrent.CompletableFuture

abstract class ContextActionsLesson : KLesson("context.actions", LessonsBundle.message("context.actions.lesson.name")) {
  abstract val sample: LessonSample
  abstract val warningQuickFix: String
  abstract val warningPossibleArea: String

  abstract val intentionText: String
  abstract val intentionCaret: String
  abstract val intentionPossibleArea: String

  override val lessonContent: LessonContext.() -> Unit = {
    prepareSample(sample)

    if (TaskTestContext.inTestMode) {
      waitBeforeContinue(1000)

      // For some reason there is no necessary hotfix in intentions, need to force IDE to update it
      task {
        val step = CompletableFuture<Boolean>()
        addStep(step)
        test {
          type(" ")
          invokeActionViaShortcut("BACK_SPACE")
          taskInvokeLater {
            step.complete(true)
          }
        }
      }
    }

    lateinit var showIntentionsTaskId: TaskContext.TaskId
    task("ShowIntentionActions") {
      showIntentionsTaskId = taskId
      text(LessonsBundle.message("context.actions.invoke.intentions.for.warning", LessonUtil.actionName(it), action(it)))
      triggerAndBorderHighlight().listItem { item ->
        item.isToStringContains(warningQuickFix)
      }
      restoreIfModifiedOrMovedIncorrectly(warningPossibleArea, sample)
      test {
        actions(it)
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
      restoreIfIntentionsPopupClosed(showIntentionsTaskId)
      test {
        ideFrame {
          jListContains(warningQuickFix).item(warningQuickFix).doubleClick()
        }
      }
    }

    caret(intentionCaret)
    task("ShowIntentionActions") {
      showIntentionsTaskId = taskId
      text(LessonsBundle.message("context.actions.invoke.general.intentions", LessonUtil.actionName(it), action(it)))
      triggerAndBorderHighlight().listItem { item ->
        item.isToStringContains(intentionText)
      }
      restoreIfModifiedOrMovedIncorrectly(intentionPossibleArea)
      test { actions(it) }
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
      test {
        ideFrame {
          jListContains(intentionText).item(intentionText).doubleClick()
        }
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

  private fun TaskContext.restoreIfIntentionsPopupClosed(restoreId: TaskContext.TaskId) {
    restoreState(restoreId = restoreId, delayMillis = defaultRestoreDelay) {
      focusOwner is EditorComponentImpl
    }
  }

  override val suitableTips = listOf("ContextActions")

  override val helpLinks: Map<String, String> get() = mapOf(
    Pair(LessonsBundle.message("context.actions.help.intention.actions"),
         LessonUtil.getHelpLink("intention-actions.html")),
  )
}
