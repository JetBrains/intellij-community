// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections.mcp

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ex.InspectionToolRegistrar
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture
import com.intellij.testFramework.fixtures.LightJavaProject
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.junit5.fixture.testFixture
import org.jetbrains.idea.devkit.inspections.quickfix.LightDevKitInspectionFixture
import org.jetbrains.idea.devkit.inspections.quickfix.lightDevKitInspectionFixture

/**
 * The fixture of the MCP tool inspection tests. Create it with [mcpToolInspectionFixture].
 *
 * The project has the stubs of the MCP annotations and of `McpToolset`. The test enables the inspection with [McpToolInspectionFixture.enableMcpInspection].
 */
class McpToolInspectionFixture(fixture: LightJavaCodeInsightTestFixture, fileExtension: String) :
  LightDevKitInspectionFixture(fixture, fileExtension) {
  /**
   * Enables the registered inspection with the short name or the ID [shortName].
   */
  fun enableMcpInspection(shortName: String) {
    val inspection = InspectionToolRegistrar.getInstance().createTools()
      .singleOrNull { it.shortName == shortName || it.id == shortName }
      ?.tool as? LocalInspectionTool ?: error("Inspection not found: $shortName")
    fixture.enableInspections(inspection)
  }
}

/**
 * Creates a [McpToolInspectionFixture] for one test. Declare it as an instance field.
 *
 * [fileExtension] is the extension of the test data files. [basePath] is the test data path under the community directory.
 */
@JvmOverloads
fun mcpToolInspectionFixture(
  projectFixture: TestFixture<LightJavaProject>,
  fileExtension: String,
  basePath: String = "",
): TestFixture<McpToolInspectionFixture> = testFixture("mcpToolInspectionFixture") {
  val devKit = lightDevKitInspectionFixture(projectFixture, fileExtension, basePath).init()
  val fixture = devKit.fixture
  fixture.addClass("package com.intellij.mcpserver.annotations; public @interface McpTool { String name() default \"\"; String title() default \"\"; }")
  fixture.addClass("package com.intellij.mcpserver.annotations; public @interface McpDescription { String description(); }")
  fixture.addClass("package com.intellij.mcpserver; public interface McpToolset {}")
  initialized(McpToolInspectionFixture(fixture, fileExtension)) {}
}
