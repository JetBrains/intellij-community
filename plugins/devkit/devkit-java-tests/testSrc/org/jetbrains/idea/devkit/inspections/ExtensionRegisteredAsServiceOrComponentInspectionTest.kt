// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections

import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.junit5.TestApplication
import org.jetbrains.idea.devkit.DevkitJavaTestsUtil
import org.junit.jupiter.api.Test

@TestDataPath("\$CONTENT_ROOT/testData/inspections/extensionRegisteredAsServiceOrComponent")
@TestApplication
class ExtensionRegisteredAsServiceOrComponentInspectionTest {
  companion object {
    private val projectFixture = pluginModuleProjectFixture()
  }

  private val extensionRegistered by extensionRegisteredAsServiceOrComponentFixture(projectFixture, "java", "${DevkitJavaTestsUtil.TESTDATA_PATH}inspections/extensionRegisteredAsServiceOrComponent")
  private val myFixture get() = extensionRegistered.fixture

  @Test
  fun testExtensionNoHighlighting() {
    extensionRegistered.setPluginXml("extension-plugin.xml")
    extensionRegistered.doTest()
  }

  @Test
  fun testServiceNoHighlighting() {
    extensionRegistered.setPluginXml("service-plugin.xml")
    extensionRegistered.doTest()
  }

  @Test
  fun testComponentNoHighlighting() {
    extensionRegistered.setPluginXml("component-plugin.xml")
    extensionRegistered.doTest()
  }

  @Test
  fun testExtensionLightService() {
    extensionRegistered.setPluginXml("extension-light-service-plugin.xml")
    extensionRegistered.doTest()
  }

  @Test
  fun testExtensionService() {
    extensionRegistered.setPluginXml("extension-service-plugin.xml")
    extensionRegistered.doTest()
  }

  @Test
  fun testExtensionComponent() {
    extensionRegistered.setPluginXml("extension-component-plugin.xml")
    extensionRegistered.doTest()
  }

  @Test
  fun testServiceWithServiceAttribute() {
    extensionRegistered.setPluginXml("service-attribute-plugin.xml")
    myFixture.testHighlighting("ServiceWithServiceAttribute.java", "ServiceAttributeBean.java")
  }
}
