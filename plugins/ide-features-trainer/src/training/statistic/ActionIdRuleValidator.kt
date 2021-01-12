// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package training.statistic

import com.intellij.internal.statistic.eventLog.validator.ValidationResultType
import com.intellij.internal.statistic.eventLog.validator.rules.EventContext
import com.intellij.internal.statistic.eventLog.validator.rules.impl.CustomValidationRule
import com.intellij.openapi.actionSystem.ActionManager
import training.statistic.FeatureUsageStatisticConsts.ACTION_ID

class ActionIdRuleValidator : CustomValidationRule() {
  override fun acceptRuleId(ruleId: String?): Boolean = (ACTION_ID == ruleId)

  override fun doValidate(data: String, context: EventContext): ValidationResultType {
    val anAction = ActionManager.getInstance().getActionOrStub(data)
    if (anAction != null) {
      return ValidationResultType.ACCEPTED
    }
    return ValidationResultType.REJECTED
  }
}