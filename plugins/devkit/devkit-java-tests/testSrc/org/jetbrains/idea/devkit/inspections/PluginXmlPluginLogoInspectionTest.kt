// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections

import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import org.jetbrains.idea.devkit.DevkitJavaTestsUtil

@TestDataPath("\$CONTENT_ROOT/testData/inspections/pluginXmlLogoInspection")
class PluginXmlPluginLogoInspectionTest : LightJavaCodeInsightFixtureTestCase() {

  override fun getBasePath(): String {
    return DevkitJavaTestsUtil.TESTDATA_PATH + "inspections/pluginXmlLogoInspection"
  }

  override fun setUp() {
    super.setUp()
    myFixture.enableInspections(PluginXmlPluginLogoInspection())
  }

  fun testPluginIconFound() {
    myFixture.addFileToProject("pluginIcon.svg", "fake SVG")
    myFixture.testHighlighting(true, true, true, "pluginIconFound.xml")
  }

  fun testPluginIconNotNecessaryForImplementationDetail() {
    myFixture.testHighlighting(true, true, true, "pluginIconNotNecessaryForImplementationDetail.xml")
  }

  fun testPluginIconNotFound() {
    myFixture.testHighlighting(true, true, true, "pluginIconNotFound.xml")
  }

}