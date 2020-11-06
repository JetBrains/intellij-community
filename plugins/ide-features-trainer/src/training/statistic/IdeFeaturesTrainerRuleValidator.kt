// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package training.statistic

import com.intellij.internal.statistic.eventLog.validator.ValidationResultType
import com.intellij.internal.statistic.eventLog.validator.rules.EventContext
import com.intellij.internal.statistic.eventLog.validator.rules.impl.CustomValidationRule
import training.learn.CourseManager
import training.statistic.FeatureUsageStatisticConsts.LESSON_ID

class IdeFeaturesTrainerRuleValidator : CustomValidationRule() {

  override fun acceptRuleId(ruleId: String?): Boolean = (ruleId == LESSON_ID)

  override fun doValidate(data: String, context: EventContext): ValidationResultType {
    return if (CourseManager.instance.modules
        .flatMap { it.lessons }
        .firstOrNull { it.id == data } != null)
      ValidationResultType.ACCEPTED
    else
      ValidationResultType.REJECTED
  }

}