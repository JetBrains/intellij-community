// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package training.learn.lesson.general.assistance

import training.commands.kotlin.TaskRuntimeContext
import training.learn.LessonsBundle
import training.learn.lesson.kimpl.KLesson
import training.learn.lesson.kimpl.LessonContext
import training.learn.lesson.kimpl.LessonSample
import training.learn.lesson.kimpl.LessonUtil.restoreIfModifiedOrMoved
import kotlin.math.min

class ParameterInfoLesson(lang: String, private val sample: LessonSample) :
  KLesson("CodeAssistance.ParameterInfo", LessonsBundle.message("parameter.info.lesson.name"), lang) {

  override val lessonContent: LessonContext.() -> Unit = {
    prepareSample(sample)

    var caretOffset = 0
    prepareRuntimeTask {
      caretOffset = editor.caretModel.offset
    }

    actionTask("ParameterInfo") {
      restoreIfModifiedOrMoved()
      LessonsBundle.message("parameter.info.use.action", action(it))
    }

    task {
      text(LessonsBundle.message("parameter.info.add.parameters", code("175"), code("100")))
      stateCheck { checkParametersAdded(caretOffset) }
      test {
        type("175, 100")
      }
    }
  }

  private val parametersRegex = Regex("""175[ \n]*,[ \n]*100[\s\S]*""")

  private fun TaskRuntimeContext.checkParametersAdded(caretOffset: Int): Boolean {
    val sequence = editor.document.charsSequence
    val partOfSequence = sequence.subSequence(caretOffset, min(caretOffset + 20, sequence.length))
    return partOfSequence.matches(parametersRegex)
  }
}