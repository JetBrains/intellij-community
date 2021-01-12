// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package training.statistic

import com.intellij.internal.statistic.eventLog.validator.ValidationResultType
import com.intellij.internal.statistic.eventLog.validator.rules.EventContext
import com.intellij.internal.statistic.eventLog.validator.rules.impl.CustomValidationRule
import training.learn.lesson.LessonManager
import training.statistic.FeatureUsageStatisticConsts.TASK_ID

class TaskIdRuleValidator : CustomValidationRule() {
  override fun acceptRuleId(ruleId: String?): Boolean = (TASK_ID == ruleId)

  override fun doValidate(data: String, context: EventContext): ValidationResultType {
    val taskId = data.toIntOrNull()
    val taskCount = LessonManager.instance.currentLessonExecutor?.taskCount
    if (taskId != null && taskCount != null && taskId in 0 until taskCount) {
      return ValidationResultType.ACCEPTED
    }
    return ValidationResultType.REJECTED
  }
}