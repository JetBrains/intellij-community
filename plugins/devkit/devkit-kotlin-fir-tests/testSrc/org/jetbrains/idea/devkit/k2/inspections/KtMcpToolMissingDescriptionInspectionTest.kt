// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.k2.inspections

import com.intellij.testFramework.TestDataPath
import org.jetbrains.idea.devkit.inspections.mcp.McpToolInspectionTestBase
import org.jetbrains.idea.devkit.kotlin.DevkitKtTestsUtil

@TestDataPath("\$CONTENT_ROOT/testData/inspections/mcpToolMissingDescription")
internal class KtMcpToolMissingDescriptionInspectionTest : McpToolInspectionTestBase() {

  override fun setUp() {
    super.setUp()
    enableMcpInspection("McpToolMissingDescription")
  }

  override fun getBasePath() = DevkitKtTestsUtil.TESTDATA_PATH + "inspections/mcpToolMissingDescription/"

  override fun getFileExtension() = "kt"

  fun testMissingDescription() {
    doTest()
  }

  fun testWithDescription() {
    doTest()
  }
}
