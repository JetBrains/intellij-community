// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package training.learn.lesson.general.refactorings

import com.intellij.ui.components.JBList
import training.dsl.*
import training.dsl.LessonUtil.restoreIfModifiedOrMoved
import training.learn.LessonsBundle
import training.learn.course.KLesson

class ExtractVariableFromBubbleLesson(private val sample: LessonSample)
  : KLesson("Extract variable", LessonsBundle.message("extract.variable.lesson.name")) {
  override val lessonContent: LessonContext.() -> Unit
    get() = {
      prepareSample(sample)
      showWarningIfInplaceRefactoringsDisabled()

      task("IntroduceVariable") {
        text(LessonsBundle.message("extract.variable.start.refactoring", action(it), code("i + 1")))
        triggerStart("IntroduceVariable")
        restoreIfModifiedOrMoved()
        test {
          actions(it)
        }
      }

      task {
        text(LessonsBundle.message("extract.variable.replace.all"))

        stateCheck {
          editor.document.text.split("i + 1").size == 2
        }
        restoreAfterStateBecomeFalse { focusOwner !is JBList<*> }
        test {
          ideFrame {
            val item = "Replace all 3 occurrences"
            jList(item).clickItem(item)
          }
        }
      }

      task("NextTemplateVariable") {
        //TODO: fix the shortcut: it should be ${action(it)} but with preference for Enter
        text(LessonsBundle.message("extract.variable.choose.name", LessonUtil.rawEnter()))
        trigger(it)
        test(waitEditorToBeReady = false) {
          actions(it)
        }
      }
    }
}
