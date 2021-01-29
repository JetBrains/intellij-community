// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog.validator.rules.utils

import com.intellij.internal.statistic.eventLog.validator.rules.beans.EventGroupContextData
import com.intellij.internal.statistic.eventLog.validator.rules.impl.EnumValidationRule
import com.intellij.internal.statistic.eventLog.validator.rules.impl.RegexpValidationRule
import com.intellij.internal.statistic.eventLog.validator.rules.utils.ValidationSimpleRuleFactory.REJECTING_UTIL_URL_PRODUCER
import com.intellij.internal.statistic.eventLog.validator.storage.GlobalRulesHolder
import org.junit.Test
import kotlin.test.assertEquals

class ValidationSimpleRuleFactoryTest {
  @Test
  fun test_parse_simple_expression() {
    assertEquals(ValidationSimpleRuleFactory.parseSimpleExpression("aa"), listOf("aa"))
    assertEquals(ValidationSimpleRuleFactory.parseSimpleExpression("aa{bb}cc"), listOf("aa", "{bb}", "cc"))
    assertEquals(ValidationSimpleRuleFactory.parseSimpleExpression("{bb}{cc}"), listOf("{bb}", "{cc}"))
    assertEquals(ValidationSimpleRuleFactory.parseSimpleExpression("a{bb}v{cc}d"), listOf("a", "{bb}", "v", "{cc}", "d"))
    assertEquals(ValidationSimpleRuleFactory.parseSimpleExpression("ccc}ddd"), listOf("ccc}ddd"))

    // incorrect
    assert(ValidationSimpleRuleFactory.parseSimpleExpression("").isEmpty())
    assert(ValidationSimpleRuleFactory.parseSimpleExpression("{aaaa").isEmpty())
    assert(ValidationSimpleRuleFactory.parseSimpleExpression("{bb}{cc").isEmpty())
    assert(ValidationSimpleRuleFactory.parseSimpleExpression("{bb{vv}vv}").isEmpty())
    assert(ValidationSimpleRuleFactory.parseSimpleExpression("{{v}").isEmpty())
  }

  @Test
  fun test_validation_rules_comparator() {
    val contextData = EventGroupContextData(emptyMap(), emptyMap(), GlobalRulesHolder(emptyMap(), emptyMap()))
    val rules = ValidationSimpleRuleFactory(REJECTING_UTIL_URL_PRODUCER)
      .getRules(hashSetOf("foo", "{enum:disabled|enabled}", "{enum:bar|baz}"), contextData)
    assertEquals(3, rules.size)
    assert(rules[0] is EnumValidationRule)
    assert(rules[1] is EnumValidationRule)
    assert(rules[2] is RegexpValidationRule)
  }
}