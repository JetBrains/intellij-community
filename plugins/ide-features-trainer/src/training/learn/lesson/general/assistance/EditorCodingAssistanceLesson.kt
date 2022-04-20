// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package training.learn.lesson.general.assistance

import com.intellij.ide.IdeBundle
import com.intellij.ui.HyperlinkLabel
import org.apache.commons.lang.StringEscapeUtils
import org.jetbrains.annotations.Nls
import training.dsl.*
import training.dsl.LessonUtil.restoreIfModifiedOrMoved
import training.learn.LessonsBundle
import training.learn.course.KLesson
import training.util.PerformActionUtil
import training.util.isToStringContains
import java.awt.Rectangle
import javax.swing.JEditorPane

abstract class EditorCodingAssistanceLesson(private val sample: LessonSample) :
  KLesson("CodeAssistance.EditorCodingAssistance", LessonsBundle.message("editor.coding.assistance.lesson.name")) {

  protected abstract val errorIntentionText: String
  protected abstract val warningIntentionText: String

  protected abstract val errorFixedText: String
  protected abstract val warningFixedText: String

  protected abstract val variableNameToHighlight: String

  override val lessonContent: LessonContext.() -> Unit = {
    prepareSample(sample)

    actionTask("GotoNextError") {
      restoreIfModifiedOrMoved(sample)
      LessonsBundle.message("editor.coding.assistance.goto.next.error", action(it))
    }

    task("ShowIntentionActions") {
      text(getFixErrorTaskText())
      stateCheck { editor.document.charsSequence.contains(errorFixedText) }
      restoreIfModifiedOrMoved()
      test {
        Thread.sleep(500)
        invokeIntention(errorIntentionText)
      }
    }

    task("GotoNextError") {
      text(LessonsBundle.message("editor.coding.assistance.goto.next.warning", action(it)))
      trigger(it)
      restoreIfModifiedOrMoved()
      test {
        Thread.sleep(500)
        actions(it)
      }
    }

    // instantly close error description popup after GotoNextError action
    prepareRuntimeTask {
      PerformActionUtil.performAction("EditorPopupMenu", editor, project)
    }

    task("ShowErrorDescription") {
      text(LessonsBundle.message("editor.coding.assistance.show.warning.description", action(it)))
      // escapeHtml required in case of hieroglyph localization
      val inspectionInfoLabelText = StringEscapeUtils.escapeHtml(IdeBundle.message("inspection.message.inspection.info"))
      triggerUI().component { ui: JEditorPane ->
        ui.text.isToStringContains(inspectionInfoLabelText)
      }
      restoreIfModifiedOrMoved()
      test {
        Thread.sleep(500)
        invokeActionViaShortcut("CONTROL F1")
        invokeActionViaShortcut("CONTROL F1")
      }
    }

    task {
      text(LessonsBundle.message("editor.coding.assistance.fix.warning") + " " + getFixWarningText())
      triggerAndBorderHighlight().componentPart { ui: HyperlinkLabel ->
        if (ui.text == warningIntentionText) {
          Rectangle(ui.x - 20, ui.y - 10, ui.width + 125, ui.height + 10)
        }
        else null
      }
      stateCheck { editor.document.charsSequence.contains(warningFixedText) }
      restoreByUi()
      test {
        Thread.sleep(500)
        invokeActionViaShortcut("ALT SHIFT ENTER")
      }
    }

    caret(variableNameToHighlight)

    task("HighlightUsagesInFile") {
      text(LessonsBundle.message("editor.coding.assistance.highlight.usages", action(it)))
      trigger(it) { checkSymbolAtCaretIsLetter() }
      restoreIfModifiedOrMoved()
      test { actions(it) }
    }
  }

  protected open fun LearningDslBase.getFixErrorTaskText(): @Nls String {
    return LessonsBundle.message("editor.coding.assistance.fix.error", action("ShowIntentionActions"),
                                 strong(errorIntentionText))
  }

  protected abstract fun getFixWarningText(): @Nls String

  private fun TaskTestContext.invokeIntention(intentionText: String) {
    actions("ShowIntentionActions")
    ideFrame {
      jList(intentionText).clickItem(intentionText)
    }
  }

  private fun TaskRuntimeContext.checkSymbolAtCaretIsLetter(): Boolean {
    val caretOffset = editor.caretModel.offset
    val sequence = editor.document.charsSequence
    return caretOffset != sequence.length && sequence[caretOffset].isLetter()
  }

  override val suitableTips = listOf("HighlightUsagesInFile", "NextPrevError", "NavigateBetweenErrors")

  override val helpLinks: Map<String, String> get() = mapOf(
    Pair(LessonsBundle.message("editor.coding.assistance.help.link"),
         LessonUtil.getHelpLink("working-with-source-code.html")),
  )
}