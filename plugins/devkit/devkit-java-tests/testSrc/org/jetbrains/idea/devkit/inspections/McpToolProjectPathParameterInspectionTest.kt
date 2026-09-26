// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections

import com.intellij.testFramework.TestDataPath
import org.jetbrains.idea.devkit.DevkitJavaTestsUtil
import org.jetbrains.idea.devkit.inspections.mcp.McpToolInspectionTestBase

@TestDataPath("\$CONTENT_ROOT/testData/inspections/mcpToolProjectPathParameter")
internal class McpToolProjectPathParameterInspectionTest : McpToolInspectionTestBase() {

  private val removeParameterFixName = "Remove 'projectPath' parameter"

  override fun setUp() {
    super.setUp()
    enableMcpInspection("McpToolProjectPathParameter")
  }

  override fun getBasePath() = DevkitJavaTestsUtil.TESTDATA_PATH + "inspections/mcpToolProjectPathParameter/"

  override fun getFileExtension() = "java"

  fun testProjectPathParameter() {
    doTest()
  }

  fun testRemoveProjectPathParameter() {
    doTest(removeParameterFixName)
  }

  fun testRemoveProjectPathMiddleParameter() {
    doTest(removeParameterFixName)
  }
}
