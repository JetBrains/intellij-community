// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package training.learn.lesson.general

import com.intellij.openapi.editor.Editor
import training.dsl.LessonContext
import training.dsl.LessonSample
import training.dsl.LessonUtil
import training.dsl.LessonUtil.restoreIfModifiedOrMoved
import training.dsl.LessonUtil.sampleRestoreNotification
import training.learn.LessonsBundle
import training.learn.course.KLesson
import kotlin.math.abs

class DuplicateLesson(private val sample: LessonSample) :
  KLesson("Duplicate", LessonsBundle.message("duplicate.and.delete.lines.lesson.name")) {
  override val lessonContent: LessonContext.() -> Unit = {
    prepareSample(sample)

    task("EditorDuplicate") {
      text(LessonsBundle.message("duplicate.and.delete.lines.duplicate.line", action(it)))
      trigger(it)
      restoreIfModifiedOrMoved(sample)
      test { actions(it) }
    }

    task("EditorUpWithSelection") {
      text(LessonsBundle.message("duplicate.and.delete.lines.select.several.lines", action(it)))
      stateCheck {
        multipleLinesSelected(editor)
      }
      test { actions(it, it) }
    }

    task("EditorDuplicate") {
      text(LessonsBundle.message("duplicate.and.delete.lines.duplicate.several.lines", action(it)))
      triggerStart(it) {
        multipleLinesSelected(editor)
      }
      proposeRestore {
        if (!multipleLinesSelected(editor)) {
          sampleRestoreNotification(LessonsBundle.message("duplicate.and.delete.lines.unexpected.selection.restore"), previous.sample)
        }
        else null
      }
      test { actions(it) }
    }

    task("EditorDeleteLine") {
      before {
        editor.selectionModel.removeSelection()
      }
      text(LessonsBundle.message("duplicate.and.delete.lines.delete.line", action(it)))
      trigger(it)
      test { actions(it) }
    }
  }

  private fun multipleLinesSelected(editor: Editor): Boolean {
    val model = editor.selectionModel
    val start = model.selectionStartPosition ?: return false
    val end = model.selectionEndPosition ?: return false
    return start.column == end.column && abs(start.line - end.line) >= 2
  }

  override val suitableTips = listOf("CtrlD", "DeleteLine")

  override val helpLinks: Map<String, String> get() = mapOf(
    Pair(LessonsBundle.message("help.lines.of.code"),
         LessonUtil.getHelpLink("working-with-source-code.html#editor_lines_code_blocks")),
  )
}