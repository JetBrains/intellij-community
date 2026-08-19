// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.k2.inspections

import com.intellij.testFramework.TestDataPath
import org.jetbrains.idea.devkit.inspections.mcp.McpToolInspectionTestBase
import org.jetbrains.idea.devkit.kotlin.DevkitKtTestsUtil

@TestDataPath("\$CONTENT_ROOT/testData/inspections/mcpToolParameterMissingDescription")
internal class KtMcpToolParameterMissingDescriptionInspectionTest : McpToolInspectionTestBase() {

  override fun setUp() {
    super.setUp()
    enableMcpInspection("McpToolParameterMissingDescription")
  }

  override fun getBasePath() = DevkitKtTestsUtil.TESTDATA_PATH + "inspections/mcpToolParameterMissingDescription/"

  override fun getFileExtension() = "kt"

  fun testMissingParameterDescription() {
    doTest()
  }
}
