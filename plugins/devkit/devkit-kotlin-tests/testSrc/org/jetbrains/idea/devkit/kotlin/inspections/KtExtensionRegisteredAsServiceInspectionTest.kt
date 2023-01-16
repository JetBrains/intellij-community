// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.kotlin.inspections

import com.intellij.testFramework.TestDataPath
import org.jetbrains.idea.devkit.inspections.ExtensionRegisteredAsServiceInspectionTestBase
import org.jetbrains.idea.devkit.kotlin.DevkitKtTestsUtil

@TestDataPath("\$CONTENT_ROOT/testData/inspections/extensionRegisteredAsService")
class KtExtensionRegisteredAsServiceInspectionTest : ExtensionRegisteredAsServiceInspectionTestBase() {

  override fun getBasePath() = "${DevkitKtTestsUtil.TESTDATA_PATH}inspections/extensionRegisteredAsService"

  override fun testExtensionNoHighlighting() {
    setUpExtensionTesting()
    setPluginXml("extension-plugin.xml")
    myFixture.testHighlighting("MyExtensionImpl.kt")
  }

  override fun testServiceNoHighlighting() {
    setPluginXml("service-plugin.xml")
    myFixture.testHighlighting("MyServiceImpl.kt")
  }

  override fun testLightServiceExtension() {
    setUpLightServiceTesting()
    setUpExtensionTesting()
    setPluginXml("extension-light-service-plugin.xml")
    myFixture.testHighlighting("MyExtensionLightServiceImpl.kt")
  }

  override fun testServiceExtension() {
    setUpExtensionTesting()
    setPluginXml("extension-service-plugin.xml")
    myFixture.testHighlighting("MyExtensionServiceImpl.kt")
  }
}
