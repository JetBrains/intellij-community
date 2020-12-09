// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package training.learn.lesson.general

import training.learn.LessonsBundle
import training.learn.interfaces.Module
import training.learn.lesson.kimpl.KLesson
import training.learn.lesson.kimpl.LessonContext
import training.learn.lesson.kimpl.LessonSample

class DuplicateLesson(module: Module, lang: String, private val sample: LessonSample) :
  KLesson("Duplicate", LessonsBundle.message("duplicate.and.delete.lines.lesson.name"), module, lang) {
  override val lessonContent: LessonContext.() -> Unit
    get() = {
      prepareSample(sample)

      actionTask("EditorDuplicate") { LessonsBundle.message("duplicate.and.delete.lines.duplicate.line", action(it)) }

      task("EditorDuplicate") {
        text(
          LessonsBundle.message("duplicate.and.delete.lines.duplicate.several.lines", action(it)))
        trigger(it, {
          val selection = editor.selectionModel
          val start = selection.selectionStartPosition?.line ?: 0
          val end = selection.selectionEndPosition?.line ?: 0
          end - start
        }, { _, new -> new >= 2 })
        test { actions("EditorUp", "EditorLineStart", "EditorDownWithSelection", "EditorDownWithSelection", it) }
      }
      actionTask("EditorDeleteLine") {
        LessonsBundle.message("duplicate.and.delete.lines.delete.line", action(it))
      }
    }
}