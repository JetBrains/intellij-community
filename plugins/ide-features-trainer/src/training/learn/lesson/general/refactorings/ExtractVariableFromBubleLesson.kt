// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package training.learn.lesson.general.refactorings

import com.intellij.testGuiFramework.impl.jList
import training.learn.LessonsBundle
import training.learn.interfaces.Module
import training.learn.lesson.kimpl.KLesson
import training.learn.lesson.kimpl.LessonContext
import training.learn.lesson.kimpl.LessonSample
import training.learn.lesson.kimpl.LessonUtil
import training.learn.lesson.kimpl.LessonUtil.restoreIfModifiedOrMoved

class ExtractVariableFromBubbleLesson(module: Module, lang: String, private val sample: LessonSample)
  : KLesson("Extract variable", LessonsBundle.message("extract.variable.lesson.name"), module, lang) {
  override val lessonContent: LessonContext.() -> Unit
    get() = {
      prepareSample(sample)
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
        test {
          ideFrame {
            val item = "Replace all 3 occurrences"
            jList(item).clickItem(item)
          }
        }
      }

      actionTask("NextTemplateVariable") {
        //TODO: fix the shortcut: it should be ${action(it)} but with preference for Enter
        LessonsBundle.message("extract.variable.choose.name", LessonUtil.rawEnter())
      }
    }
}
