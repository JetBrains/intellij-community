// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package training.learn.lesson.general.refactorings

import com.intellij.refactoring.RefactoringBundle
import com.intellij.refactoring.introduce.inplace.OccurrencesChooser.BaseReplaceChoice
import com.intellij.refactoring.rename.inplace.InplaceRefactoring
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

      fun actionString(n: Int) = RefactoringBundle.message("replace.all.occurrences", n)
      task("IntroduceVariable") {
        text(LessonsBundle.message("extract.variable.start.refactoring", action(it), code("i + 1")))
        triggerStart("IntroduceVariable")
        restoreIfModifiedOrMoved(sample)
        test {
          actions(it)
        }
      }

      task {
        transparentRestore = true
        triggerAndBorderHighlight().listItem { item ->
          item is BaseReplaceChoice && item.formatDescription(3) == actionString(3)
        }
        restoreByTimer() // the refactoring may be called from the wrong place
      }

      task {
        text(LessonsBundle.message("extract.variable.replace.all"))

        stateCheck {
          editor.getUserData(InplaceRefactoring.INPLACE_RENAMER) != null
        }
        restoreByUi(delayMillis = defaultRestoreDelay)
        test {
          ideFrame {
            val item = actionString(3)
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

      restoreRefactoringOptionsInformer()
    }

  override val suitableTips = listOf("IntroduceVariable")

  override val helpLinks: Map<String, String> get() = mapOf(
    Pair(LessonsBundle.message("extract.variable.help.link"),
         LessonUtil.getHelpLink("extract-variable.html")),
  )
}
