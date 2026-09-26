// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections

import com.intellij.testFramework.TestDataPath
import org.jetbrains.idea.devkit.DevkitJavaTestsUtil
import org.jetbrains.idea.devkit.inspections.mcp.McpToolInspectionTestBase

@TestDataPath("\$CONTENT_ROOT/testData/inspections/mcpToolMissingDescription")
internal class McpToolMissingDescriptionInspectionTest : McpToolInspectionTestBase() {

  override fun setUp() {
    super.setUp()
    enableMcpInspection("McpToolMissingDescription")
  }

  override fun getBasePath() = DevkitJavaTestsUtil.TESTDATA_PATH + "inspections/mcpToolMissingDescription/"

  override fun getFileExtension() = "java"

  fun testMissingDescription() {
    doTest()
  }

  fun testWithDescription() {
    doTest()
  }
}
