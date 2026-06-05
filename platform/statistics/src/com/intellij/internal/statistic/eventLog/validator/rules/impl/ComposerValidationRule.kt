// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.eventLog.validator.rules.impl

import com.jetbrains.fus.reporting.api.FUSRule
import com.jetbrains.fus.reporting.api.IEventContext
import com.jetbrains.fus.reporting.api.ValidationResultType

class ComposerValidationRule(val rules: List<FUSRule>) {
  fun doValidate(data: String, context: IEventContext): ValidationResultType {
    val isAccepted = rules.any { it.validate(data, context) == ValidationResultType.ACCEPTED }
    return if (isAccepted) ValidationResultType.ACCEPTED else ValidationResultType.REJECTED
  }
}
