// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistics.metadata.storage

import com.intellij.internal.statistic.eventLog.validator.IntellijSensitiveDataValidator
import com.intellij.internal.statistic.eventLog.validator.rules.EventContext
import com.intellij.internal.statistic.eventLog.validator.rules.impl.CustomValidationRule
import com.intellij.internal.statistic.eventLog.validator.storage.GroupValidationTestRule
import com.intellij.internal.statistic.eventLog.validator.storage.ValidationTestRulesPersistedStorage
import com.intellij.openapi.util.Disposer
import com.jetbrains.fus.reporting.api.FUSRule
import com.jetbrains.fus.reporting.api.IEventContext
import com.jetbrains.fus.reporting.api.ValidationResultType
import org.assertj.core.api.Assertions.assertThat

internal class CustomRuleReferenceTest : ValidationRulesBaseStorageTest() {
  fun testRuleFollowsTheRegistrationOfTheExtension() {
    val validator = IntellijSensitiveDataValidator.getInstance(recorderId)
    val rule = ReferenceTestRule()
    val ruleDisposable = Disposer.newDisposable(testRootDisposable)
    CustomValidationRule.EP_NAME.point.registerExtension(rule, ruleDisposable)
    ValidationTestRulesPersistedStorage.getTestStorage(recorderId, true)!!.addTestGroup(GroupValidationTestRule(
      groupId,
      true,
      """{ "event_id" : [ "{enum:started}" ], "event_data" : { "name" : [ "{util#${rule.ruleId}}" ] } }"""
    ))
    val fieldRule = eventDataRules(validator).single()
    assertThat(fieldRule).isNotSameAs(rule)
    assertThat(validate(fieldRule)).isEqualTo(ValidationResultType.ACCEPTED)

    Disposer.dispose(ruleDisposable)
    assertThat(validate(fieldRule)).isEqualTo(ValidationResultType.INCORRECT_RULE)

    CustomValidationRule.EP_NAME.point.registerExtension(ReferenceTestRule(), testRootDisposable)
    assertThat(validate(fieldRule)).isEqualTo(ValidationResultType.ACCEPTED)
    assertThat(IntellijSensitiveDataValidator.getInstance(recorderId)).isSameAs(validator)
  }

  private fun validate(rule: FUSRule): ValidationResultType = rule.validate("value", EventContext.create("started", emptyMap()))

  private fun eventDataRules(validator: IntellijSensitiveDataValidator): List<FUSRule> {
    val groupRules = validator.validationRulesStorage.getGroupValidators(groupId).eventGroupRules ?: return emptyList()
    return groupRules.getEventDataRules().values.flatMap { it.asList() }
  }
}

private class ReferenceTestRule : CustomValidationRule() {
  override fun getRuleId(): String = "reference_test_rule"

  override fun doValidate(data: String, context: IEventContext): ValidationResultType = ValidationResultType.ACCEPTED
}
