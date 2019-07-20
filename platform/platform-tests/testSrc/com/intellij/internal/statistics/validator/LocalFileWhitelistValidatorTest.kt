// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistics.validator

import com.intellij.internal.statistic.eventLog.FeatureUsageData
import com.intellij.internal.statistic.eventLog.validator.ValidationResultType
import com.intellij.internal.statistic.eventLog.validator.rules.EventContext
import com.intellij.internal.statistic.eventLog.validator.rules.impl.CustomWhiteListRule
import com.intellij.internal.statistic.eventLog.validator.rules.impl.LocalFileCustomWhiteListRule
import com.intellij.testFramework.LightPlatformTestCase
import junit.framework.TestCase
import org.junit.Test

class LocalFileWhitelistValidatorTest : LightPlatformTestCase() {

  private fun doValidateEventId(validator: CustomWhiteListRule, eventId: String, eventData: FeatureUsageData) {
    val context = EventContext.create(eventId, eventData.build())
    doTest(ValidationResultType.ACCEPTED, validator, eventId, context)
  }

  private fun doRejectEventId(validator: CustomWhiteListRule, eventId: String, eventData: FeatureUsageData) {
    val context = EventContext.create(eventId, eventData.build())
    doTest(ValidationResultType.REJECTED, validator, eventId, context)
  }

  private fun doTest(expected: ValidationResultType, validator: CustomWhiteListRule, data: String, context: EventContext) {
    TestCase.assertEquals(expected, validator.validate(data, context))
  }

  @Test
  fun `test validate first allowed value by file`() {
    val validator = TestLocalFileWhitelistValidator()
    doValidateEventId(validator, "allowed.value", FeatureUsageData())
  }

  @Test
  fun `test validate second allowed value by file`() {
    val validator = TestLocalFileWhitelistValidator()
    doValidateEventId(validator, "another.allowed.value", FeatureUsageData())
  }

  @Test
  fun `test reject unknown value`() {
    val validator = TestLocalFileWhitelistValidator()
    doRejectEventId(validator, "unknown.value", FeatureUsageData())
  }

  @Test
  fun `test reject value if file doesn't exist`() {
    val validator = EmptyLocalFileWhitelistValidator()
    doRejectEventId(validator, "value", FeatureUsageData())
  }
}

private class TestLocalFileWhitelistValidator : LocalFileCustomWhiteListRule("local_file",
                                                                     LocalFileWhitelistValidatorTest::class.java,
                                                                     "file-with-allowed-values.txt")

private class EmptyLocalFileWhitelistValidator : LocalFileCustomWhiteListRule("local_file",
                                                                              LocalFileWhitelistValidatorTest::class.java,
                                                                              "not-existing-file.txt")