// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections.quickfix

import org.jetbrains.idea.devkit.inspections.UseHtmlChunkToolTipInspection

abstract class UseHtmlChunkToolTipInspectionTestBase : LightDevKitInspectionFixTestBase() {
  @Throws(Exception::class)
  override fun setUp() {
    super.setUp()
    myFixture.enableInspections(UseHtmlChunkToolTipInspection())
    myFixture.addClass(
      """package javax.swing;
        |public class JComponent {
        |  public String getToolTipText() { return ""; }
        |  public void setToolTipText(String text) {}
        |}""".trimMargin()
    )
    myFixture.addClass(
      """package com.intellij.openapi.util.text;
        |public class HtmlChunk {
        |  public static HtmlChunk text(String text) { return new HtmlChunk(); }
        |  public static HtmlChunk raw(String text) { return new HtmlChunk(); }
        |}""".trimMargin()
    )
    myFixture.addClass(
      """package com.intellij.ide;
        |import javax.swing.JComponent;
        |import com.intellij.openapi.util.text.HtmlChunk;
        |public class HelpTooltipKt {
        |  public static void setToolTipText(JComponent comp, HtmlChunk html) {}
        |}""".trimMargin()
    )
    addSetToolTipTextExtensionStub()
  }

  protected open fun addSetToolTipTextExtensionStub() {}
}
