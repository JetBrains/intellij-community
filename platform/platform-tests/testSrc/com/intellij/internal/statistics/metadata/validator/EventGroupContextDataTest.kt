// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistics.metadata.validator

import com.intellij.internal.statistic.eventLog.validator.rules.beans.EventGroupContextData
import com.intellij.internal.statistic.eventLog.validator.storage.GlobalRulesHolder
import org.junit.Test
import kotlin.test.assertNotSame
import kotlin.test.assertSame

class EventGroupContextDataTest {
  @Test
  fun `test caching global regexps`() {
    val contextData = EventGroupContextData(emptyMap(), emptyMap(), GlobalRulesHolder(emptyMap(), hashMapOf("integer" to "-?\\d+(\\+)?")))
    assertSame(contextData.getRegexpValidationRule("integer"), contextData.getRegexpValidationRule("integer"))
  }

  @Test
  fun `test caching global enums`() {
    val booleanValues: Set<String> = hashSetOf("true", "false", "TRUE", "FALSE")
    val contextData = EventGroupContextData(emptyMap(), emptyMap(), GlobalRulesHolder(hashMapOf("boolean" to booleanValues), emptyMap()))
    assertSame(contextData.getEnumValidationRule("boolean"), contextData.getEnumValidationRule("boolean"))
  }

  @Test
  fun `test not caching unknown regexps`() {
    val booleanValues: Set<String> = hashSetOf("true", "false", "TRUE", "FALSE")
    val contextData = EventGroupContextData(emptyMap(), hashMapOf("integer" to "-?\\d+(\\+)?"),
                                            GlobalRulesHolder(hashMapOf("boolean" to booleanValues), emptyMap()))
    assertNotSame(contextData.getRegexpValidationRule("unknown"), contextData.getRegexpValidationRule("unknown"))
  }
}