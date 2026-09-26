// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections.mcp

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ex.InspectionToolRegistrar
import org.jetbrains.idea.devkit.inspections.quickfix.LightDevKitInspectionFixTestBase

/**
 * Shared setup for the MCP tool-authoring inspection tests. Stubs the MCP annotation API so test data can
 * reference @McpTool / @McpDescription without a compile dependency on intellij.mcpserver. Concrete
 * subclasses enable the specific inspection under test.
 */
abstract class McpToolInspectionTestBase : LightDevKitInspectionFixTestBase() {

  override fun setUp() {
    super.setUp()
    myFixture.addClass("package com.intellij.mcpserver.annotations; public @interface McpTool { String name() default \"\"; String title() default \"\"; }")
    myFixture.addClass("package com.intellij.mcpserver.annotations; public @interface McpDescription { String description(); }")
    myFixture.addClass("package com.intellij.mcpserver; public interface McpToolset {}")
  }

  protected fun enableMcpInspection(shortName: String) {
    val inspection = InspectionToolRegistrar.getInstance().createTools()
      .singleOrNull { it.shortName == shortName || it.id == shortName }
      ?.tool as? LocalInspectionTool ?: error("Inspection not found: $shortName")
    myFixture.enableInspections(inspection)
  }
}
