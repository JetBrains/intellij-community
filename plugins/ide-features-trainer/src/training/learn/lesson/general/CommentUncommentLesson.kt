// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package training.learn.lesson.general

import com.intellij.psi.PsiComment
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import training.dsl.LessonContext
import training.dsl.LessonSample
import training.dsl.LessonUtil
import training.dsl.TaskRuntimeContext
import training.learn.LessonsBundle.message
import training.learn.course.KLesson

class CommentUncommentLesson(private val sample: LessonSample, private val blockCommentsAvailable: Boolean = false)
  : KLesson("Comment line",
            if(blockCommentsAvailable)
              message("comment.block.lesson.name")
            else message("comment.line.lesson.name")) {

  override val lessonContent: LessonContext.() -> Unit
    get() = {
      fun TaskRuntimeContext.countCommentedLines(): Int =
        calculateComments(PsiDocumentManager.getInstance(project).getPsiFile(editor.document)!!)

      prepareSample(sample)

      actionTask("CommentByLineComment") {
        message("comment.line.comment.any.line", action(it))
      }
      task("CommentByLineComment") {
        text(message("comment.line.uncomment.that.line", action(it)))
        trigger(it, { countCommentedLines() }, { _, now -> now == 0 })
        test { actions("EditorUp", it) }
      }
      task("CommentByLineComment") {
        text(message("comment.line.comment.several.lines", action(it)))
        trigger(it, { countCommentedLines() }, { before, now -> now >= before + 2 })
        test { actions("EditorDownWithSelection", "EditorDownWithSelection", it) }
      }
      task("CommentByLineComment") {
        text(message("comment.line.uncomment.several.lines", action(it)))
        trigger(it, { countCommentedLines() }, { _, now -> now == 0 })
        test { actions(it) }
      }
      if (blockCommentsAvailable) {
        actionTask("CommentByBlockComment") {
          message("comment.block.comment", code("/*...*/"), action(it))
        }
        task("CommentByBlockComment") {
          text(message("comment.block.uncomment", action(it)))
          trigger(it, { countCommentedLines() }, { _, now -> now == 0 })
          test { actions(it) }
        }
      }
    }

  private fun calculateComments(psiElement: PsiElement): Int {
    return when {
      psiElement is PsiComment -> 1
      psiElement.children.isEmpty() -> 0
      else -> {
        var result = 0
        for (astChild in psiElement.node.getChildren(null)) {
          val psiChild = astChild.psi
          result += calculateComments(psiChild)
        }
        result
      }
    }
  }

  override val suitableTips = listOf("CommentCode")

  override val helpLinks: Map<String, String> get() = mapOf(
    Pair(message("help.lines.of.code"),
         LessonUtil.getHelpLink("working-with-source-code.html#editor_lines_code_blocks")),
  )
}
