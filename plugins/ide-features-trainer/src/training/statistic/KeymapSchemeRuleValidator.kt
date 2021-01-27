// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package training.statistic

import com.intellij.internal.statistic.eventLog.validator.ValidationResultType
import com.intellij.internal.statistic.eventLog.validator.rules.EventContext
import com.intellij.internal.statistic.eventLog.validator.rules.impl.CustomValidationRule
import com.intellij.openapi.keymap.KeymapManager
import com.intellij.openapi.keymap.impl.DefaultKeymapImpl
import training.statistic.FeatureUsageStatisticConsts.KEYMAP_SCHEME

class KeymapSchemeRuleValidator : CustomValidationRule() {
  override fun acceptRuleId(ruleId: String?): Boolean = (KEYMAP_SCHEME == ruleId)

  override fun doValidate(data: String, context: EventContext): ValidationResultType {
    val keymap = KeymapManager.getInstance().getKeymap(data)
    if (keymap is DefaultKeymapImpl) {
      return ValidationResultType.ACCEPTED
    }
    return ValidationResultType.REJECTED
  }
}