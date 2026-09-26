// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.codeInsight

import org.jetbrains.idea.devkit.DevkitJavaTestsUtil
import org.jetbrains.idea.devkit.inspections.PluginXmlRegistrationCheckInspectionTestBase

class PluginXmlRegistrationCheckInspectionTest : PluginXmlRegistrationCheckInspectionTestBase() {

  override val sourceFileExtension: String = "java"

  override fun getBasePath(): String {
    return DevkitJavaTestsUtil.TESTDATA_PATH + "inspections/pluginXmlRegistrationCheck"
  }

  fun testRegistrationCheck() {
    doTestRegistrationCheck()
  }
}
