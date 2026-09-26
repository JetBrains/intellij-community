// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.eventLog.trace

import com.intellij.internal.statistic.eventLog.validator.rules.impl.CustomValidationRule
import com.jetbrains.fus.reporting.api.IEventContext
import com.jetbrains.fus.reporting.api.ValidationResultType
import org.jetbrains.annotations.ApiStatus

// The rule ids are frozen: published event metadata already refers to them.
private const val RAW_CODE_RULE_ID = "llm_code_parameters"
private const val RAW_TEXT_RULE_ID = "llm_text_parameters"

/**
 * Accepts any value, so a field declared with it carries raw content into the TRACE plane.
 *
 * Subclasses differ only in [getRuleId], which tells the metadata reviewer and `TraceLlmPiiDataFilter` whether the
 * value is code-shaped or text-shaped.
 */
@Suppress("UnstableApiUsage")
@ApiStatus.Internal
abstract class TraceRawValidationRule : CustomValidationRule() {
  final override fun doValidate(data: String, context: IEventContext): ValidationResultType = ValidationResultType.ACCEPTED

  companion object {
    /**
     * Every rule id of this hierarchy. `TraceLlmPiiDataFilter` redacts by this set rather than by the registered
     * extensions, so that redaction does not depend on extension initialisation order.
     * A new subclass has to be listed here; `TraceRedactionGuardTest` fails when it is not.
     */
    @JvmField
    val RULE_IDS: Set<String> = setOf(RAW_CODE_RULE_ID, RAW_TEXT_RULE_ID)
  }
}

@Suppress("UnstableApiUsage")
@ApiStatus.Internal
class TraceRawCodeValidationRule : TraceRawValidationRule() {
  override fun getRuleId(): String = RAW_CODE_RULE_ID
}

@Suppress("UnstableApiUsage")
@ApiStatus.Internal
class TraceRawTextValidationRule : TraceRawValidationRule() {
  override fun getRuleId(): String = RAW_TEXT_RULE_ID
}
