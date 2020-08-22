// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistics.whitelist.validator

import com.intellij.featureStatistics.FeatureUsageTrackerImpl
import com.intellij.internal.statistic.collectors.fus.FacetTypeUsageCollector
import com.intellij.internal.statistic.eventLog.FeatureUsageData
import com.intellij.internal.statistic.eventLog.validator.ValidationResultType
import com.intellij.internal.statistic.eventLog.validator.rules.EventContext
import com.intellij.internal.statistic.eventLog.validator.rules.impl.CustomValidationRule
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.LightPlatformTestCase
import junit.framework.TestCase
import org.junit.Test

class FeatureUsageCustomValidatorsTest : LightPlatformTestCase() {

  private fun doValidateEventId(validator: CustomValidationRule, eventId: String, eventData: FeatureUsageData) {
    val context = EventContext.create(eventId, eventData.build())
    doTest(ValidationResultType.ACCEPTED, validator, context.eventId, context)
  }

  private fun doValidateEventData(validator: CustomValidationRule, name: String, eventData: FeatureUsageData) {
    val context = EventContext.create("event_id", eventData.build())
    doTest(ValidationResultType.ACCEPTED, validator, context.eventData[name] as String, context)
  }

  private fun doRejectEventId(validator: CustomValidationRule, eventId: String, eventData: FeatureUsageData) {
    val context = EventContext.create(eventId, eventData.build())
    doTest(ValidationResultType.REJECTED, validator, context.eventId, context)
  }

  private fun doTest(expected: ValidationResultType, validator: CustomValidationRule, data: String, context: EventContext) {
    TestCase.assertEquals(expected, validator.validate(data, context))
  }

  @Test
  fun `test validate invalid facet`() {
    val validator = FacetTypeUsageCollector.FacetTypeUtilValidator()
    doValidateEventId(validator, "invalid", FeatureUsageData())
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
  fun `test validate module facet by id with spaces`() {
    val disposable = Disposer.newDisposable()
    try {
      TestUsageStatisticsFacetType.registerTestFacetTypeWithSpace(disposable)
      val validator = FacetTypeUsageCollector.FacetTypeUtilValidator()
      doValidateEventId(validator, "Test Usage Statistics Facet", FeatureUsageData())
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
  fun `test rejected unknown module facet with spaces`() {
    val disposable = Disposer.newDisposable()
    try {
      TestUsageStatisticsFacetType.registerTestFacetType(disposable)
      val validator = FacetTypeUsageCollector.FacetTypeUtilValidator()
      doRejectEventId(validator, "Unknown Facet", FeatureUsageData())
    }
    finally {
      Disposer.dispose(disposable)
    }
  }

  @Test
  fun `test validate welcome productivity feature id`() {
    val validator = FeatureUsageTrackerImpl.ProductivityUtilValidator()
    val data = FeatureUsageData().addData("id", "features.welcome").addData("group", "unknown")
    doValidateEventData(validator, "id", data)
  }

  @Test
  fun `test validate unknown productivity feature group`() {
    val validator = FeatureUsageTrackerImpl.ProductivityUtilValidator()
    val data = FeatureUsageData().addData("id", "features.welcome").addData("group", "unknown")
    doValidateEventData(validator, "group", data)
  }

  @Test
  fun `test validate third party productivity feature`() {
    val validator = FeatureUsageTrackerImpl.ProductivityUtilValidator()
    doValidateEventId(validator, "third.party", FeatureUsageData())
  }

  @Test
  fun `test reject unknown productivity feature`() {
    val validator = FeatureUsageTrackerImpl.ProductivityUtilValidator()
    doRejectEventId(validator, "unknown.feature.id", FeatureUsageData())
  }
}