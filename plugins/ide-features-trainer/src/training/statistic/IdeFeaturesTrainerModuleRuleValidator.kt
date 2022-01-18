// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package training.statistic

import com.intellij.internal.statistic.eventLog.validator.ValidationResultType
import com.intellij.internal.statistic.eventLog.validator.rules.EventContext
import com.intellij.internal.statistic.eventLog.validator.rules.impl.CustomValidationRule
import training.learn.CourseManager
import training.statistic.FeatureUsageStatisticConsts.MODULE_NAME

private class IdeFeaturesTrainerModuleRuleValidator : CustomValidationRule() {
  override fun acceptRuleId(ruleId: String?): Boolean = (ruleId == MODULE_NAME)

  override fun doValidate(data: String, context: EventContext): ValidationResultType {
    return if (CourseManager.instance.modules.any { it.id == data && !CourseManager.instance.isModuleExternal(it) })
      ValidationResultType.ACCEPTED
    else
      ValidationResultType.REJECTED
  }
}