// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections.quickfix

import com.intellij.testFramework.fixtures.LightJavaProject
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.junit5.fixture.testFixture
import org.jetbrains.idea.devkit.inspections.UseHtmlChunkToolTipInspection

/**
 * Creates a [LightDevKitInspectionFixture] for one test. Declare it as an instance field.
 *
 * [fileExtension] is the extension of the test data files. [basePath] is the test data path under the community directory.
 * The fixture enables [UseHtmlChunkToolTipInspection]. The project has the stubs of `JComponent`, `HtmlChunk` and `HelpTooltipKt`.
 */
@JvmOverloads
fun useHtmlChunkToolTipFixture(
  projectFixture: TestFixture<LightJavaProject>,
  fileExtension: String,
  basePath: String = "",
): TestFixture<LightDevKitInspectionFixture> = testFixture("useHtmlChunkToolTipFixture") {
  val devKit = lightDevKitInspectionFixture(projectFixture, fileExtension, basePath).init()
  val fixture = devKit.fixture
  fixture.enableInspections(UseHtmlChunkToolTipInspection())
  fixture.addClass(
    """package javax.swing;
      |public class JComponent {
      |  public String getToolTipText() { return ""; }
      |  public void setToolTipText(String text) {}
      |}""".trimMargin()
  )
  fixture.addClass(
    """package com.intellij.openapi.util.text;
      |public class HtmlChunk {
      |  public static HtmlChunk text(String text) { return new HtmlChunk(); }
      |  public static HtmlChunk raw(String text) { return new HtmlChunk(); }
      |}""".trimMargin()
  )
  fixture.addClass(
    """package com.intellij.ide;
      |import javax.swing.JComponent;
      |import com.intellij.openapi.util.text.HtmlChunk;
      |public class HelpTooltipKt {
      |  public static void setToolTipText(JComponent comp, HtmlChunk html) {}
      |}""".trimMargin()
  )
  initialized(devKit) {}
}
