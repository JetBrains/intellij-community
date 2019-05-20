// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistics.validator

import com.intellij.featureStatistics.FeatureUsageTrackerImpl
import com.intellij.internal.statistic.collectors.fus.FacetTypeUsageCollector
import com.intellij.internal.statistic.eventLog.FeatureUsageData
import com.intellij.internal.statistic.eventLog.validator.ValidationResultType
import com.intellij.internal.statistic.eventLog.validator.rules.EventContext
import com.intellij.internal.statistic.eventLog.validator.rules.impl.CustomUtilsWhiteListRule
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.LightPlatformTestCase
import junit.framework.TestCase
import org.junit.Test

class FeatureUsageCustomValidatorsTest : LightPlatformTestCase() {

  private fun doValidateEventId(validator: CustomUtilsWhiteListRule, eventId: String, eventData: FeatureUsageData) {
    val context = EventContext.create(eventId, eventData.build())
    doTest(ValidationResultType.ACCEPTED, validator, eventId, context)
  }

  private fun doRejectEventId(validator: CustomUtilsWhiteListRule, eventId: String, eventData: FeatureUsageData) {
    val context = EventContext.create(eventId, eventData.build())
    doTest(ValidationResultType.REJECTED, validator, eventId, context)
  }

  private fun doTest(expected: ValidationResultType, validator: CustomUtilsWhiteListRule, data: String, context: EventContext) {
    TestCase.assertEquals(expected, validator.validate(data, context))
  }

  @Test
  fun `test validate module facet by id`() {
    val disposable = Disposer.newDisposable()
    try {
      TestUsageStatisticsFacetType.registerTestFacetType(disposable)
      val validator = FacetTypeUsageCollector.FacetTypeUtilValidator()
      doValidateEventId(validator, "TestUsageStatisticsFacet", FeatureUsageData())
    }
    finally {
      Disposer.dispose(disposable)
    }
  }

  @Test
  fun `test validate third party module facet`() {
    val validator = FacetTypeUsageCollector.FacetTypeUtilValidator()
    doValidateEventId(validator, "third.party", FeatureUsageData())
  }

  @Test
  fun `test rejected unknown module facet`() {
    val disposable = Disposer.newDisposable()
    try {
      TestUsageStatisticsFacetType.registerTestFacetType(disposable)
      val validator = FacetTypeUsageCollector.FacetTypeUtilValidator()
      doRejectEventId(validator, "UnknownFacet", FeatureUsageData())
    }
    finally {
      Disposer.dispose(disposable)
    }
  }

  @Test
  fun `test validate welcome productivity feature`() {
    val validator = FeatureUsageTrackerImpl.ProductivityUtilValidator()
    doValidateEventId(validator, "features.welcome", FeatureUsageData())
  }

  @Test
  fun `test validate third party productivity feature`() {
    val validator = FeatureUsageTrackerImpl.ProductivityUtilValidator()
    doValidateEventId(validator, "third.party", FeatureUsageData())
  }

  @Test
  fun `test validate productivity feature by id`() {
    val validator = FeatureUsageTrackerImpl.ProductivityUtilValidator()
    doValidateEventId(validator, "navigation.popup.camelprefix", FeatureUsageData())
  }

  @Test
  fun `test reject unknown productivity feature`() {
    val validator = FeatureUsageTrackerImpl.ProductivityUtilValidator()
    doRejectEventId(validator, "unknown.feature.id", FeatureUsageData())
  }
}