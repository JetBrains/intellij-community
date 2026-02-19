// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger;

import com.intellij.openapi.editor.markup.HighlighterLayer;
import com.intellij.xdebugger.ui.DebuggerColors;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

public class XDebuggerColorsTest {
  @Test
  public void testBreakPointLineHighlighterMustNotOverwriteAdditionalSyntaxOnTheCurrentLine() {
    //noinspection ConstantValue
    assertTrue("Debugger current line highlighter must not cover additional syntax", DebuggerColors.BREAKPOINT_HIGHLIGHTER_LAYER < HighlighterLayer.ADDITIONAL_SYNTAX);
  }
}
