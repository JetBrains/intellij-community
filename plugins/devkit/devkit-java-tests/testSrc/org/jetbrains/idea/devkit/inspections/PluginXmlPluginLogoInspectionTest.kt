// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections

import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.fixtures.lightJavaCodeInsightFixture
import com.intellij.testFramework.fixtures.lightJavaProjectFixture
import com.intellij.testFramework.junit5.TestApplication
import org.jetbrains.idea.devkit.DevkitJavaTestsUtil
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@TestDataPath("\$CONTENT_ROOT/testData/inspections/pluginXmlLogoInspection")
@TestApplication
class PluginXmlPluginLogoInspectionTest {
  companion object {
    private val projectFixture = lightJavaProjectFixture()
  }

  private val myFixture by lightJavaCodeInsightFixture(projectFixture, DevkitJavaTestsUtil.TESTDATA_PATH + "inspections/pluginXmlLogoInspection")

  @BeforeEach
  fun setUp() {
    myFixture.enableInspections(PluginXmlPluginLogoInspection())
  }

  @Test
  fun testPluginIconFound() {
    myFixture.addFileToProject("pluginIcon.svg", "fake SVG")
    myFixture.testHighlighting(true, true, true, "pluginIconFound.xml")
  }

  @Test
  fun testPluginIconNotNecessaryForImplementationDetail() {
    myFixture.testHighlighting(true, true, true, "pluginIconNotNecessaryForImplementationDetail.xml")
  }

  @Test
  fun testPluginIconNotFound() {
    myFixture.testHighlighting(true, true, true, "pluginIconNotFound.xml")
  }

}