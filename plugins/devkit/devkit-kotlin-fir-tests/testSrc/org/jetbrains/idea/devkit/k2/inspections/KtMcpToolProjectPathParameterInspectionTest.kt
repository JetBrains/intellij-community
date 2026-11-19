// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.k2.inspections

import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.fixtures.lightJavaProjectFixture
import com.intellij.testFramework.junit5.TestApplication
import org.jetbrains.idea.devkit.inspections.mcp.mcpToolInspectionFixture
import org.jetbrains.idea.devkit.kotlin.DevkitKtTestsUtil
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@TestDataPath("\$CONTENT_ROOT/testData/inspections/mcpToolProjectPathParameter")
@TestApplication
internal class KtMcpToolProjectPathParameterInspectionTest {
  companion object {
    private val projectFixture = lightJavaProjectFixture()
  }

  private val mcpTool by mcpToolInspectionFixture(projectFixture, "kt", DevkitKtTestsUtil.TESTDATA_PATH + "inspections/mcpToolProjectPathParameter/")

  private val removeParameterFixName = "Remove 'projectPath' parameter"

  @BeforeEach
  fun setUp() {
    mcpTool.enableMcpInspection("McpToolProjectPathParameter")
  }

  @Test
  fun testProjectPathParameter() {
    mcpTool.doTest()
  }

  @Test
  fun testRemoveProjectPathParameter() {
    mcpTool.doTest(removeParameterFixName)
  }

  @Test
  fun testRemoveProjectPathWithDefaultValueAndComment() {
    mcpTool.doTest(removeParameterFixName)
  }
}
