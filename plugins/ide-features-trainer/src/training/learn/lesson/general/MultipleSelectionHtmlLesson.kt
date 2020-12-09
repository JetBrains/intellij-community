// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package training.learn.lesson.general

import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.html.HtmlTag
import com.intellij.psi.util.PsiTreeUtil
import training.commands.kotlin.TaskRuntimeContext
import training.learn.LessonsBundle
import training.learn.interfaces.Module
import training.learn.lesson.kimpl.KLesson
import training.learn.lesson.kimpl.LessonContext
import training.learn.lesson.kimpl.parseLessonSample

class MultipleSelectionHtmlLesson(module: Module)
  : KLesson("Multiple selections", LessonsBundle.message("multiple.selections.lesson.name"), module, "HTML") {
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
        LessonsBundle.message("multiple.selections.select.symbol", action(it))
      }
      actionTask("SelectNextOccurrence") {
        LessonsBundle.message("multiple.selections.select.next.symbol", action(it))
      }
      actionTask("UnselectPreviousOccurrence") {
        LessonsBundle.message("multiple.selections.deselect.symbol", action(it))
      }
      actionTask("SelectAllOccurrences") {
        LessonsBundle.message("multiple.selections.select.all", action(it))
      }
      task {
        text(LessonsBundle.message("multiple.selections.replace", code("td"), code("th")))
        stateCheck { checkMultiChange() }
        test { type("td") }
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
}