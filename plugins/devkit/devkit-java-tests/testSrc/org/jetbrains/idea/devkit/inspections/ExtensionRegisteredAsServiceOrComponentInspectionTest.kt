// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections

import com.intellij.testFramework.TestDataPath
import org.jetbrains.idea.devkit.DevkitJavaTestsUtil

@TestDataPath("\$CONTENT_ROOT/testData/inspections/extensionRegisteredAsServiceOrComponent")
class ExtensionRegisteredAsServiceOrComponentInspectionTest : ExtensionRegisteredAsServiceOrComponentInspectionTestBase() {
  override fun getBasePath() = "${DevkitJavaTestsUtil.TESTDATA_PATH}inspections/extensionRegisteredAsServiceOrComponent"

  override fun getFileExtension() = "java"

  fun testExtensionNoHighlighting() {
    setPluginXml("extension-plugin.xml")
    doTest()
  }

  fun testServiceNoHighlighting() {
    setPluginXml("service-plugin.xml")
    doTest()
  }

  fun testComponentNoHighlighting() {
    setPluginXml("component-plugin.xml")
    doTest()
  }

  fun testExtensionLightService() {
    setPluginXml("extension-light-service-plugin.xml")
    doTest()
  }

  fun testExtensionService() {
    setPluginXml("extension-service-plugin.xml")
    doTest()
  }

  fun testExtensionComponent() {
    setPluginXml("extension-component-plugin.xml")
    doTest()
  }

  fun testServiceWithServiceAttribute() {
    setPluginXml("service-attribute-plugin.xml")
    myFixture.testHighlighting("ServiceWithServiceAttribute.java", "ServiceAttributeBean.java")
  }
}
