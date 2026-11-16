// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.k2.inspections

import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.fixtures.lightJavaProjectFixture
import com.intellij.testFramework.junit5.TestApplication
import org.jetbrains.idea.devkit.inspections.mcp.mcpToolInspectionFixture
import org.jetbrains.idea.devkit.kotlin.DevkitKtTestsUtil
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@TestDataPath("\$CONTENT_ROOT/testData/inspections/mcpToolParameterMissingDescription")
@TestApplication
internal class KtMcpToolParameterMissingDescriptionInspectionTest {
  companion object {
    private val projectFixture = lightJavaProjectFixture()
  }

  private val mcpTool by mcpToolInspectionFixture(projectFixture, "kt", DevkitKtTestsUtil.TESTDATA_PATH + "inspections/mcpToolParameterMissingDescription/")

  @BeforeEach
  fun setUp() {
    mcpTool.enableMcpInspection("McpToolParameterMissingDescription")
  }

  @Test
  fun testMissingParameterDescription() {
    mcpTool.doTest()
  }
}
