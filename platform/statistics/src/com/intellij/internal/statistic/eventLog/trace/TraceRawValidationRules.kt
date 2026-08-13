// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.eventLog.trace

import com.intellij.internal.statistic.eventLog.validator.rules.impl.CustomValidationRule
import com.jetbrains.fus.reporting.api.IEventContext
import com.jetbrains.fus.reporting.api.ValidationResultType
import org.jetbrains.annotations.ApiStatus

/**
 * Accepts any value, so a field declared with it carries raw content into the TRACE plane.
 *
 * Subclasses differ only in [getRuleId], which tells the metadata reviewer and `TraceLlmPiiDataFilter` whether the
 * value is code-shaped or text-shaped. The ids are frozen: published event metadata already refers to them.
 */
@Suppress("UnstableApiUsage")
@ApiStatus.Internal
abstract class TraceRawValidationRule : CustomValidationRule() {
  final override fun doValidate(data: String, context: IEventContext): ValidationResultType = ValidationResultType.ACCEPTED
}

@Suppress("UnstableApiUsage")
@ApiStatus.Internal
class TraceRawCodeValidationRule : TraceRawValidationRule() {
  override fun getRuleId(): String = "llm_code_parameters"
}

@Suppress("UnstableApiUsage")
@ApiStatus.Internal
class TraceRawTextValidationRule : TraceRawValidationRule() {
  override fun getRuleId(): String = "llm_text_parameters"
}
