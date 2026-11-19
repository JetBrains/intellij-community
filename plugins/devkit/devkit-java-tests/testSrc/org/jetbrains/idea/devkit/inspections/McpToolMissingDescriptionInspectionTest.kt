// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections

import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.fixtures.lightJavaProjectFixture
import com.intellij.testFramework.junit5.TestApplication
import org.jetbrains.idea.devkit.DevkitJavaTestsUtil
import org.jetbrains.idea.devkit.inspections.mcp.mcpToolInspectionFixture
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@TestDataPath("\$CONTENT_ROOT/testData/inspections/mcpToolMissingDescription")
@TestApplication
internal class McpToolMissingDescriptionInspectionTest {
  companion object {
    private val projectFixture = lightJavaProjectFixture()
  }

  private val mcpTool by mcpToolInspectionFixture(projectFixture, "java", DevkitJavaTestsUtil.TESTDATA_PATH + "inspections/mcpToolMissingDescription/")

  @BeforeEach
  fun setUp() {
    mcpTool.enableMcpInspection("McpToolMissingDescription")
  }

  @Test
  fun testMissingDescription() {
    mcpTool.doTest()
  }

  @Test
  fun testWithDescription() {
    mcpTool.doTest()
  }
}
