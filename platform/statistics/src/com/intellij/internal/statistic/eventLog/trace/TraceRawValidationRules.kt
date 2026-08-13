// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.eventLog.trace

import com.intellij.internal.statistic.eventLog.validator.rules.impl.CustomValidationRule
import com.jetbrains.fus.reporting.api.IEventContext
import org.jetbrains.annotations.ApiStatus

/**
 * Accept-all FUS validation rule for LLM-generated field values.
 *
 * Concrete subclasses ([TrueValidationRuleCode], [TrueValidationRuleText]) differ
 * only in [getRuleId] so that downstream consumers (PII filter, metadata reviewers)
 * can distinguish code-shaped from text-shaped LLM output.
 */
@Suppress("UnstableApiUsage")
@ApiStatus.Internal
abstract class TrueValidationRule : CustomValidationRule() {
  final override fun doValidate(data: String, context: IEventContext): com.jetbrains.fus.reporting.api.ValidationResultType =
    com.jetbrains.fus.reporting.api.ValidationResultType.ACCEPTED
}

@Suppress("UnstableApiUsage")
@ApiStatus.Internal
class TrueValidationRuleCode : TrueValidationRule() {
  override fun getRuleId(): String = "llm_code_parameters"
}

@Suppress("UnstableApiUsage")
@ApiStatus.Internal
class TrueValidationRuleText : TrueValidationRule() {
  override fun getRuleId(): String = "llm_text_parameters"
}
