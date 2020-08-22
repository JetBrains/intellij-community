// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistics.whitelist.validator

import com.intellij.featureStatistics.*
import com.intellij.internal.statistic.eventLog.FeatureUsageData
import com.intellij.internal.statistic.eventLog.validator.ValidationResultType
import com.intellij.internal.statistic.eventLog.validator.rules.EventContext
import com.intellij.internal.statistic.eventLog.validator.rules.impl.CustomValidationRule
import com.intellij.openapi.application.ApplicationManager
import com.intellij.testFramework.registerExtension
import junit.framework.TestCase
import org.junit.Test

class ProductivityValidatorTest : ProductivityFeaturesTest() {

  private fun doValidateEventData(validator: CustomValidationRule, name: String, eventData: FeatureUsageData) {
    val context = EventContext.create("event_id", eventData.build())
    val data = context.eventData[name] as String
    TestCase.assertEquals(ValidationResultType.ACCEPTED, validator.validate(data, context))
  }

  override fun setUp() {
    super.setUp()
    ApplicationManager.getApplication().registerExtension(
      ProductivityFeaturesProvider.EP_NAME, TestProductivityFeatureProvider(),
      testRootDisposable
    )
  }

  @Test
  fun `test validate productivity feature by id`() {
    val validator = FeatureUsageTrackerImpl.ProductivityUtilValidator()
    val data = FeatureUsageData().addData("id", "testFeatureId").addData("group", "testFeatureGroup")
    doValidateEventData(validator, "id", data)
  }

  @Test
  fun `test validate productivity feature by group`() {
    val validator = FeatureUsageTrackerImpl.ProductivityUtilValidator()
    val data = FeatureUsageData().addData("id", "testFeatureId").addData("group", "testFeatureGroup")
    doValidateEventData(validator, "group", data)
  }

  @Test
  fun `test validate productivity feature by id without group`() {
    val validator = FeatureUsageTrackerImpl.ProductivityUtilValidator()
    val data = FeatureUsageData().addData("id", "secondTestFeatureId").addData("group", "unknown")
    doValidateEventData(validator, "id", data)
  }

  @Test
  fun `test validate productivity feature by unknown group`() {
    val validator = FeatureUsageTrackerImpl.ProductivityUtilValidator()
    val data = FeatureUsageData().addData("id", "secondTestFeatureId").addData("group", "unknown")
    doValidateEventData(validator, "group", data)
  }
}

class TestProductivityFeatureProvider : ProductivityFeaturesProvider() {
  override fun getFeatureDescriptors(): Array<FeatureDescriptor> {
    val withGroup = FeatureDescriptor("testFeatureId", "testFeatureGroup", "TestTip.html", "test", 0, 0, null, 0, this)
    val noGroup = FeatureDescriptor("secondTestFeatureId", null, "TestTip.html", "test", 0, 0, null, 0, this)
    return arrayOf(withGroup, noGroup)
  }

  override fun getGroupDescriptors(): Array<GroupDescriptor> {
    return arrayOf(GroupDescriptor("testFeatureGroup", "test"))
  }

  override fun getApplicabilityFilters(): Array<ApplicabilityFilter?> {
    return arrayOfNulls(0)
  }
}