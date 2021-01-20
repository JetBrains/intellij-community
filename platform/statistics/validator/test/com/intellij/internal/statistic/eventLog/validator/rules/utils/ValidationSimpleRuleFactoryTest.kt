// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog.validator.rules.utils

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
}