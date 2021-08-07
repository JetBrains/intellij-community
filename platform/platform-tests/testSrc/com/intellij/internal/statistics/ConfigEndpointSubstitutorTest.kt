// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistics

import com.intellij.internal.statistic.eventLog.EventLogInternalApplicationInfo
import com.intellij.internal.statistic.eventLog.EventLogEndpointSubstitutor
import com.intellij.openapi.application.ApplicationManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import junit.framework.TestCase

class ConfigEndpointSubstitutorTest : BasePlatformTestCase() {

  companion object {
    private const val URL = "https://localhost/"
  }

  private class TestEndpointSubstitutor: EventLogEndpointSubstitutor {
    override fun getTemplateUrl(recorderId: String): String = URL
  }

  override fun setUp() {
    super.setUp()
    installEP()
  }

  fun installEP() {
    val ep = ApplicationManager.getApplication().extensionArea.getExtensionPoint(EventLogEndpointSubstitutor.EP_NAME)
    ep.registerExtension(TestEndpointSubstitutor(), project)
  }

  fun testSubstitution() {
    val applicationInfo = EventLogInternalApplicationInfo("FUS", true)
    TestCase.assertEquals(URL, applicationInfo.templateUrl)
  }

}