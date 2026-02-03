// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package training.learn.lesson.general.navigation

import com.intellij.find.SearchTextArea
import com.intellij.usageView.UsageViewBundle
import training.dsl.LessonContext
import training.dsl.LessonSample
import training.dsl.LessonUtil
import training.dsl.LessonUtil.restoreIfModifiedOrMoved
import training.learn.LessonsBundle
import training.learn.course.KLesson
import training.learn.course.LessonType

abstract class OccurrencesLesson : KLesson("java.occurrences.lesson", LessonsBundle.message("find.occurrences.lesson.name")) {

  override val lessonType: LessonType = LessonType.SINGLE_EDITOR

  abstract  val sample: LessonSample
  override val lessonContent: LessonContext.() -> Unit = {
    prepareSample(sample)

    task("Find") {
      text(LessonsBundle.message("find.occurrences.invoke.find", code("cellphone"), action(it)))
      triggerUI().component { _: SearchTextArea -> true }
      restoreIfModifiedOrMoved(sample)
      test { actions(it) }
    }
    task("FindNext") {
      @Suppress("UnresolvedPluginConfigReference", "InjectedReferences")
      trigger("com.intellij.find.editorHeaderActions.NextOccurrenceAction")
      text(LessonsBundle.message("find.occurrences.find.next", LessonUtil.rawEnter(), action(it)))
      restoreByUi()
      test {
        ideFrame {
          actionButton(UsageViewBundle.message("action.next.occurrence")).click()
        }
      }
    }
    task("FindPrevious") {
      @Suppress("UnresolvedPluginConfigReference", "InjectedReferences")
      trigger("com.intellij.find.editorHeaderActions.PrevOccurrenceAction")
      text(LessonsBundle.message("find.occurrences.find.previous", action("FindPrevious")))
      showWarning(LessonsBundle.message("find.occurrences.search.closed.warning", action("Find"))) {
        editor.headerComponent == null
      }
      test {
        ideFrame {
          actionButton(UsageViewBundle.message("action.previous.occurrence")).click()
        }
      }
    }
    task("EditorEscape") {
      text(LessonsBundle.message("find.occurrences.close.search.tool", action(it)))
      stateCheck {
        editor.headerComponent == null
      }
      test { invokeActionViaShortcut("ESCAPE") }
    }
    actionTask("FindNext") {
      LessonsBundle.message("find.occurrences.find.next.in.editor", action(it))
    }
    actionTask("FindPrevious") {
      LessonsBundle.message("find.occurrences.find.previous.in.editor", action(it))
    }
    text(LessonsBundle.message("find.occurrences.note.about.cyclic", action("FindNext"), action("FindPrevious")))
  }

  override val helpLinks: Map<String, String>
    get() = mapOf(
      Pair(LessonsBundle.message("find.help.link"),
           LessonUtil.getHelpLink("finding-and-replacing-text-in-file.html")),
    )
}