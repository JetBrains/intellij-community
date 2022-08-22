// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.eventLog.validator.rules.impl

import com.intellij.internal.statistic.eventLog.validator.ValidationResultType
import com.intellij.internal.statistic.eventLog.validator.rules.EventContext
import com.intellij.internal.statistic.eventLog.validator.rules.FUSRule

class ComposerValidationRule(val rules: List<FUSRule>) {

  fun doValidate(data: String, context: EventContext): ValidationResultType {
    for (rule in rules) {
      val validationResultType = rule.validate(data, context)
      if (validationResultType == ValidationResultType.ACCEPTED)
        return ValidationResultType.ACCEPTED
    }
    return ValidationResultType.REJECTED
  }
}