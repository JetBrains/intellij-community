// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package training.learn.lesson.general.assistance

import com.intellij.ide.IdeBundle
import com.intellij.testGuiFramework.framework.GuiTestUtil
import com.intellij.testGuiFramework.impl.jList
import com.intellij.testGuiFramework.util.Key
import com.intellij.testGuiFramework.util.Modifier
import com.intellij.testGuiFramework.util.Shortcut
import training.commands.kotlin.TaskContext
import training.commands.kotlin.TaskRuntimeContext
import training.commands.kotlin.TaskTestContext
import training.learn.LessonsBundle
import training.learn.interfaces.Module
import training.learn.lesson.kimpl.KLesson
import training.learn.lesson.kimpl.LessonContext
import training.learn.lesson.kimpl.LessonSample
import training.learn.lesson.kimpl.LessonUtil.restoreIfModifiedOrMoved
import training.util.PerformActionUtil
import javax.swing.JEditorPane

abstract class EditorCodingAssistanceLesson(module: Module, lang: String, private val sample: LessonSample) :
  KLesson("CodeAssistance.EditorCodingAssistance", LessonsBundle.message("editor.coding.assistance.lesson.name"), module, lang) {

  protected abstract val errorIntentionText: String
  protected abstract val warningIntentionText: String

  protected abstract val errorFixedText: String
  protected abstract val warningFixedText: String

  protected abstract val variableNameToHighlight: String

  override val lessonContent: LessonContext.() -> Unit = {
    prepareSample(sample)

    actionTask("GotoNextError") {
      restoreIfModifiedOrMoved()
      LessonsBundle.message("editor.coding.assistance.goto.next.error", action(it))
    }

    fixErrorUsingIntentionTask(errorIntentionText, errorFixedText) { addFixErrorTaskText() }

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
      PerformActionUtil.performAction("EditorPopupMenu", editor, project) {}
    }

    task("ShowErrorDescription") {
      text(LessonsBundle.message("editor.coding.assistance.show.warning.description", action(it)))
      val inspectionInfoLabelText = IdeBundle.message("inspection.message.inspection.info")
      triggerByUiComponentAndHighlight<JEditorPane>(false, false) { ui ->
        ui.text.contains(inspectionInfoLabelText)
      }
      restoreIfModifiedOrMoved()
      test {
        Thread.sleep(500)
        val errorDescriptionShortcut = Shortcut(hashSetOf(Modifier.CONTROL), Key.F1)
        GuiTestUtil.shortcut(errorDescriptionShortcut)
        GuiTestUtil.shortcut(errorDescriptionShortcut)
      }
    }

    fixErrorUsingIntentionTask(warningIntentionText, warningFixedText) {
      text(LessonsBundle.message("editor.coding.assistance.fix.warning", action("ShowIntentionActions"), strong(warningIntentionText)))
    }

    caret(variableNameToHighlight)

    task("HighlightUsagesInFile") {
      text(LessonsBundle.message("editor.coding.assistance.highlight.usages", action(it)))
      trigger(it) { checkSymbolAtCaretIsLetter() }
      restoreIfModifiedOrMoved()
      test { actions(it) }
    }
  }

  private fun LessonContext.fixErrorUsingIntentionTask(errorIntentionText: String,
                                                       errorFixedText: String,
                                                       addText: TaskContext.() -> Unit) {
    task("ShowIntentionActions") {
      addText()
      triggerByListItemAndHighlight { item -> isHighlightedListItem(item.toString()) }
      stateCheck { editor.document.charsSequence.contains(errorFixedText) }
      restoreIfModifiedOrMoved()
      test {
        Thread.sleep(500)
        invokeIntention(errorIntentionText)
      }
    }
  }

  protected open fun isHighlightedListItem(item: String): Boolean {
    return item == errorIntentionText || item == warningIntentionText
  }


  protected open fun TaskContext.addFixErrorTaskText() {
    text(LessonsBundle.message("editor.coding.assistance.fix.error", action("ShowIntentionActions"), strong(errorIntentionText)))
  }

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
}