// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package training.learn.lesson.general.assistance

import training.dsl.*
import training.dsl.LessonUtil.restoreIfModifiedOrMoved
import training.dsl.LessonUtil.sampleRestoreNotification
import training.learn.LessonsBundle
import training.learn.course.KLesson
import kotlin.math.min

class ParameterInfoLesson(private val sample: LessonSample) :
  KLesson("CodeAssistance.ParameterInfo", LessonsBundle.message("parameter.info.lesson.name")) {

  override val lessonContent: LessonContext.() -> Unit = {
    prepareSample(sample)
    val initialOffset = sample.getPosition(0).startOffset

    actionTask("ParameterInfo") {
      restoreIfModifiedOrMoved(sample)
      LessonsBundle.message("parameter.info.use.action", action(it))
    }

    task {
      text(LessonsBundle.message("parameter.info.add.parameters", code("175"), code("100")))
      stateCheck { checkParametersAdded(initialOffset) }
      proposeRestore {
        if (initialOffset >= editor.document.textLength) {
          sampleRestoreNotification(TaskContext.ModificationRestoreProposal, sample)
        }
        else null
      }
      test {
        type("175, 100")
      }
    }
  }

  private val parametersRegex = Regex("""175[ \n]*,[ \n]*100[\s\S]*""")

  private fun TaskRuntimeContext.checkParametersAdded(initialOffset: Int): Boolean {
    val sequence = editor.document.charsSequence
    if (initialOffset >= sequence.length) return false
    val partOfSequence = sequence.subSequence(initialOffset, min(initialOffset + 20, sequence.length))
    return partOfSequence.matches(parametersRegex)
  }

  override val suitableTips = listOf("ParameterInfo")

  override val helpLinks: Map<String, String> get() = mapOf(
    Pair(LessonsBundle.message("parameter.info.help.link"),
         LessonUtil.getHelpLink("viewing-reference-information.html#view-parameter-info")),
  )
}