// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package training.learn.lesson.general

import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.html.HtmlTag
import com.intellij.psi.util.PsiTreeUtil
import training.dsl.LessonContext
import training.dsl.LessonUtil
import training.dsl.LessonUtil.restoreIfModifiedOrMoved
import training.dsl.TaskRuntimeContext
import training.dsl.parseLessonSample
import training.learn.LessonsBundle
import training.learn.course.KLesson

class MultipleSelectionHtmlLesson
  : KLesson("Multiple selections", LessonsBundle.message("multiple.selections.lesson.name")) {

  override val languageId: String = "HTML"

  private val sample = parseLessonSample("""<!doctype html>
<html lang="en">
    <head>
        <meta charset="UTF-8">
        <title>Multiple selections</title>
    </head>
    <body>
        <table>
            <tr>
                <<caret>th>Firstname</th>
                <th>Lastname</th>
                <th>Points</th>
            </tr>
            <tr>
                <td>Eve</td>
                <td>Jackson</td>
                <td>94</td>
            </tr>
        </table>
    </body>
</html>
""".trimIndent())

  override val lessonContent: LessonContext.() -> Unit
    get() = {
      prepareSample(sample)

      actionTask("SelectNextOccurrence") {
        restoreIfModifiedOrMoved()
        LessonsBundle.message("multiple.selections.select.symbol", action(it))
      }
      actionTask("SelectNextOccurrence") {
        restoreState {
          sample.text != editor.document.text || editor.selectionModel.selectedText != "th"
        }
        LessonsBundle.message("multiple.selections.select.next.symbol", action(it))
      }
      actionTask("UnselectPreviousOccurrence") {
        restoreState {
          sample.text != editor.document.text || editor.caretModel.caretCount < 2
        }
        LessonsBundle.message("multiple.selections.deselect.symbol", action(it))
      }
      actionTask("SelectAllOccurrences") {
        restoreIfModifiedOrMoved()
        LessonsBundle.message("multiple.selections.select.all", action(it))
      }
      task {
        text(LessonsBundle.message("multiple.selections.replace", code("td"), code("th")))
        restoreState {
          editor.caretModel.caretCount != 6
        }
        stateCheck { checkMultiChange() }
        test { type("td") }
      }
      task("EditorEscape") {
        stateCheck {
          editor.caretModel.caretCount < 2
        }
        text(LessonsBundle.message("multiple.selections.escape", action(it)))
        test { actions(it) }
      }
    }

  private fun TaskRuntimeContext.checkMultiChange(): Boolean {
    val psiFile = PsiDocumentManager.getInstance(project).getPsiFile(editor.document)

    val childrenOfType1 = PsiTreeUtil.findChildrenOfType(psiFile, HtmlTag::class.java)

    var count = 0

    for (htmlTag in childrenOfType1) {
      if (htmlTag.name == "th") return false
      if (htmlTag.name == "td") count++
    }
    return count == 6
  }

  override val helpLinks: Map<String, String> get() = mapOf(
    Pair(LessonsBundle.message("multiple.selections.help.multiple.carets"),
         LessonUtil.getHelpLink("multicursor.html")),
  )
}