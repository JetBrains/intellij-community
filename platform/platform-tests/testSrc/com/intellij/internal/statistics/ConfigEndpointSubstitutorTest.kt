// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("DEPRECATION")

package com.intellij.internal.statistics

import com.intellij.internal.statistic.eventLog.EventLogEndpointSubstitutor
import com.intellij.internal.statistic.eventLog.EventLogInternalApplicationInfo
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.assertj.core.api.Assertions.assertThat

private const val URL = "https://localhost/"

class ConfigEndpointSubstitutorTest : BasePlatformTestCase() {
  private class TestEndpointSubstitutor: EventLogEndpointSubstitutor {
    override fun getTemplateUrl(recorderId: String) = URL
  }

  override fun setUp() {
    super.setUp()
    installEp()
  }

  fun installEp() {
    ExtensionTestUtil.maskExtensions(EventLogEndpointSubstitutor.EP_NAME, listOf(TestEndpointSubstitutor()), testRootDisposable)
  }

  fun testSubstitution() {
    val applicationInfo = EventLogInternalApplicationInfo("FUS", true)
    assertThat(applicationInfo.templateUrl).isEqualTo(URL)
  }
}