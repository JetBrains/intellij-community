// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package training.statistic

import com.intellij.internal.statistic.eventLog.validator.ValidationResultType
import com.intellij.internal.statistic.eventLog.validator.rules.EventContext
import com.intellij.internal.statistic.eventLog.validator.rules.impl.CustomValidationRule
import com.intellij.internal.statistic.utils.getPluginInfoByDescriptor
import training.lang.LangManager
import training.statistic.FeatureUsageStatisticConsts.LANGUAGE

private class SupportedLanguageRuleValidator : CustomValidationRule() {
  override fun acceptRuleId(ruleId: String?): Boolean = (LANGUAGE == ruleId)

  override fun doValidate(data: String, context: EventContext): ValidationResultType {
    val langSupport = LangManager.getInstance().supportedLanguagesExtensions.find {
      getPluginInfoByDescriptor(it.pluginDescriptor).isDevelopedByJetBrains() && it.language.equals(data, ignoreCase = true)
    }
    if (langSupport != null) {
      return ValidationResultType.ACCEPTED
    }
    return ValidationResultType.REJECTED
  }
}