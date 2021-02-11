// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package training.learn.lesson.general

import training.learn.LessonsBundle
import training.learn.lesson.kimpl.KLesson
import training.learn.lesson.kimpl.LessonContext
import training.learn.lesson.kimpl.LessonSample

class SelectLesson(lang: String, private val sample: LessonSample) :
  KLesson("Select", LessonsBundle.message("selection.lesson.name"), lang) {
  override val lessonContent: LessonContext.() -> Unit
    get() = {
      prepareSample(sample)

      actionTask("EditorNextWordWithSelection") {
        LessonsBundle.message("selection.select.word", action(it))
      }
      actionTask("EditorSelectWord") {
        LessonsBundle.message("selection.extend.selection", action(it))
      }
      task("EditorSelectWord") {
        text(LessonsBundle.message("selection.extend.until.whole.file", action(it)))
        trigger(it) {
          editor.selectionModel.selectionStart == 0 && editor.document.textLength == editor.selectionModel.selectionEnd
        }
        test {
          for (i in 1..9) {
            actions(it)
          }
        }
      }
      actionTask("EditorUnSelectWord") {
        LessonsBundle.message("selection.shrink.selection", action(it))
      }
    }
}