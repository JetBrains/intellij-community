// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package training.statistic

import com.intellij.internal.statistic.eventLog.validator.ValidationResultType
import com.intellij.internal.statistic.eventLog.validator.rules.EventContext
import com.intellij.internal.statistic.eventLog.validator.rules.impl.CustomValidationRule
import training.dsl.LessonContext
import training.dsl.TaskContext
import training.learn.CourseManager
import training.learn.course.KLesson
import training.statistic.FeatureUsageStatisticConsts.LESSON_ID
import training.statistic.FeatureUsageStatisticConsts.TASK_ID

private class TaskIdRuleValidator : CustomValidationRule() {
  override fun acceptRuleId(ruleId: String?): Boolean = (TASK_ID == ruleId)

  override fun doValidate(data: String, context: EventContext): ValidationResultType {
    val taskId = data.toIntOrNull()
    val lessonId = context.eventData[LESSON_ID]
    if (taskId != null && lessonId is String) {
      val lesson = CourseManager.instance.findLessonById(lessonId)
      if (lesson is KLesson) {
        val taskCount = lesson.getTaskCount()
        if (taskId in 0 until taskCount) {
          return ValidationResultType.ACCEPTED
        }
      }
    }
    return ValidationResultType.REJECTED
  }

  private fun KLesson.getTaskCount(): Int {
    val context = ExtractTaskCountContext(this)
    lessonContent(context)
    return context.taskCount
  }
}

private class ExtractTaskCountContext(override val lesson: KLesson) : LessonContext() {
  var taskCount = 0

  override fun task(taskContent: TaskContext.() -> Unit) {
    taskCount++
  }

  override fun waitBeforeContinue(delayMillis: Int) {
    taskCount++
  }
}