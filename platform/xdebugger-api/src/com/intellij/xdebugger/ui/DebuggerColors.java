// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.xdebugger.ui;

import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.markup.HighlighterLayer;
import com.intellij.openapi.util.Key;

public interface DebuggerColors {
  TextAttributesKey BREAKPOINT_ATTRIBUTES = TextAttributesKey.createTextAttributesKey("BREAKPOINT_ATTRIBUTES");
  TextAttributesKey EXECUTIONPOINT_ATTRIBUTES = TextAttributesKey.createTextAttributesKey("EXECUTIONPOINT_ATTRIBUTES");
  TextAttributesKey NOT_TOP_FRAME_ATTRIBUTES = TextAttributesKey.createTextAttributesKey("NOT_TOP_FRAME_ATTRIBUTES");
  TextAttributesKey EVALUATED_EXPRESSION_ATTRIBUTES = TextAttributesKey.createTextAttributesKey("EVALUATED_EXPRESSION_ATTRIBUTES");
  TextAttributesKey EVALUATED_EXPRESSION_EXECUTION_LINE_ATTRIBUTES = TextAttributesKey.createTextAttributesKey("EVALUATED_EXPRESSION_EXECUTION_LINE_ATTRIBUTES");

  int BREAKPOINT_HIGHLIGHTER_LAYER = HighlighterLayer.ADDITIONAL_SYNTAX - 20;

  Key<Boolean> BREAKPOINT_HIGHLIGHTER_KEY = Key.create("BREAKPOINT_HIGHLIGHTER_KEY");

  // Define the execution line highlighter layer to be less that smart step into one (for the smart step into highlighter it is HighlighterLayer.SELECTION - 1)
  int EXECUTION_LINE_HIGHLIGHTERLAYER = HighlighterLayer.SELECTION - 2;
  TextAttributesKey INLINED_VALUES = TextAttributesKey.createTextAttributesKey("DEBUGGER_INLINED_VALUES");
  TextAttributesKey INLINED_VALUES_MODIFIED = TextAttributesKey.createTextAttributesKey("DEBUGGER_INLINED_VALUES_MODIFIED");
  TextAttributesKey INLINED_VALUES_EXECUTION_LINE = TextAttributesKey.createTextAttributesKey("DEBUGGER_INLINED_VALUES_EXECUTION_LINE");
  TextAttributesKey SMART_STEP_INTO_TARGET = TextAttributesKey.createTextAttributesKey("DEBUGGER_SMART_STEP_INTO_TARGET", EditorColors.SEARCH_RESULT_ATTRIBUTES);
  TextAttributesKey SMART_STEP_INTO_SELECTION = TextAttributesKey.createTextAttributesKey("DEBUGGER_SMART_STEP_INTO_SELECTION", EditorColors.LIVE_TEMPLATE_ATTRIBUTES);
  TextAttributesKey INLINE_STACK_FRAMES = TextAttributesKey.createTextAttributesKey("INLINE_STACK_FRAMES");
}
