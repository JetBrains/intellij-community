// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.eventLog.validator.rules.utils

import com.intellij.internal.statistic.eventLog.validator.rules.impl.CustomValidationRule
import com.intellij.internal.statistic.eventLog.validator.rules.impl.TestModeValidationRule
import com.intellij.internal.statistic.eventLog.validator.rules.impl.UtilValidationRule
import com.jetbrains.fus.reporting.api.IEventContext
import com.jetbrains.fus.reporting.api.ValidationResultType

private val NO_RULE = Any()

/**
 * Validates with the [CustomValidationRule] extension that accepts [ruleId].
 *
 * The rules of a validator keep this reference instead of the extension, so a plugin unload leaves no extension in them.
 * The keyed cache of [CustomValidationRule.EP_NAME] holds the found extension.
 * Each change of the extension point clears that cache, so a loaded rule is also found with no reload of the rules.
 */
internal class CustomRuleReference(private val ruleId: String, private val testMode: Boolean) : UtilValidationRule {
  fun resolve(): CustomValidationRule? {
    val rule = CustomValidationRule.EP_NAME.computeIfAbsent(ruleId, CustomRuleReference::class.java) { id ->
      findRule(id) ?: NO_RULE
    } as? CustomValidationRule ?: return null
    // the cache is shared by all recorders, so the test mode of the recorder is checked after the lookup
    return if (rule is TestModeValidationRule && !testMode) null else rule
  }

  override fun validate(data: String, context: IEventContext): ValidationResultType {
    return resolve()?.validate(data, context) ?: ValidationResultType.INCORRECT_RULE
  }

  override fun toString(): String = "CustomRuleReference(ruleId=$ruleId)"
}

private fun findRule(ruleId: String): CustomValidationRule? {
  return CustomValidationRule.EP_NAME.extensionList.firstOrNull {
    CustomRuleProducer.isDevelopedByJetBrains(it.javaClass) && it.acceptRuleId(ruleId)
  }
}
