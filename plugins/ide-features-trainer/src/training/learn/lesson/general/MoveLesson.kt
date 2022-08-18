// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package training.learn.lesson.general

import training.dsl.LessonContext
import training.dsl.LessonSample
import training.dsl.LessonUtil
import training.dsl.LessonUtil.restoreIfModifiedOrMoved
import training.learn.LessonsBundle
import training.learn.course.KLesson

class MoveLesson(private val caretText: String, private val sample: LessonSample)
  : KLesson("Move", LessonsBundle.message("move.lesson.name")) {
  override val lessonContent: LessonContext.() -> Unit
    get() = {
      prepareSample(sample)

      actionTask("MoveLineDown") {
        restoreIfModifiedOrMoved(sample)
        LessonsBundle.message("move.pull.down", action(it))
      }
      actionTask("MoveLineUp") {
        restoreIfModifiedOrMoved()
        LessonsBundle.message("move.pull.up", action(it))
      }
      caret(caretText)
      task("MoveStatementUp") {
        restoreIfModifiedOrMoved()
        text(LessonsBundle.message("move.whole.method.up", action(it)))
        trigger(it, { editor.document.text }, { before, now ->
          checkSwapMoreThan2Lines(before, now)
        })
        test { actions(it) }
      }
      actionTask("MoveStatementDown") {
        restoreIfModifiedOrMoved()
        LessonsBundle.message("move.whole.method.down", action(it))
      }
    }

  override val suitableTips = listOf("MoveUpDown")

  override val helpLinks: Map<String, String> get() = mapOf(
    Pair(LessonsBundle.message("help.lines.of.code"),
         LessonUtil.getHelpLink("working-with-source-code.html#editor_lines_code_blocks")),
  )
}

fun checkSwapMoreThan2Lines(before: String, now: String): Boolean {
  val beforeLines = before.split("\n")
  val nowLines = now.split("\n")

  if (beforeLines.size != nowLines.size) {
    return false
  }
  val change = beforeLines.size - commonPrefix(beforeLines, nowLines) - commonSuffix(beforeLines, nowLines)
  return change >= 4
}

private fun <T> commonPrefix(list1: List<T>, list2: List<T>): Int {
  val size = Integer.min(list1.size, list2.size)
  for (i in 0 until size) {
    if (list1[i] != list2[i])
      return i
  }
  return size
}

private fun <T> commonSuffix(list1: List<T>, list2: List<T>): Int {
  val size = Integer.min(list1.size, list2.size)
  for (i in 0 until size) {
    if (list1[list1.size - i - 1] != list2[list2.size - i - 1])
      return i
  }
  return size
}
