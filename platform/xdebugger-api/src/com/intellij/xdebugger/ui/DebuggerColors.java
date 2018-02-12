/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.intellij.xdebugger.ui;

import com.intellij.openapi.editor.colors.ColorKey;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.markup.HighlighterLayer;
import com.intellij.openapi.util.Key;

import java.awt.*;

public interface DebuggerColors {
  TextAttributesKey BREAKPOINT_ATTRIBUTES = TextAttributesKey.createTextAttributesKey("BREAKPOINT_ATTRIBUTES");
  TextAttributesKey EXECUTIONPOINT_ATTRIBUTES = TextAttributesKey.createTextAttributesKey("EXECUTIONPOINT_ATTRIBUTES");
  TextAttributesKey NOT_TOP_FRAME_ATTRIBUTES = TextAttributesKey.createTextAttributesKey("NOT_TOP_FRAME_ATTRIBUTES");
  TextAttributesKey EVALUATED_EXPRESSION_ATTRIBUTES = TextAttributesKey.createTextAttributesKey("EVALUATED_EXPRESSION_ATTRIBUTES");
  TextAttributesKey EVALUATED_EXPRESSION_EXECUTION_LINE_ATTRIBUTES = TextAttributesKey.createTextAttributesKey("EVALUATED_EXPRESSION_EXECUTION_LINE_ATTRIBUTES");
  ColorKey RECURSIVE_CALL_ATTRIBUTES = ColorKey.createColorKey("RECURSIVE_CALL_ATTRIBUTES", new Color(255, 255, 215));

  int BREAKPOINT_HIGHLIGHTER_LAYER = HighlighterLayer.ADDITIONAL_SYNTAX + 1;

  Key<Boolean> BREAKPOINT_HIGHLIGHTER_KEY = Key.create("BREAKPOINT_HIGHLIGHTER_KEY");
  int EXECUTION_LINE_HIGHLIGHTERLAYER = HighlighterLayer.SELECTION - 1;
  TextAttributesKey INLINED_VALUES = TextAttributesKey.createTextAttributesKey("DEBUGGER_INLINED_VALUES");
  TextAttributesKey INLINED_VALUES_MODIFIED = TextAttributesKey.createTextAttributesKey("DEBUGGER_INLINED_VALUES_MODIFIED");
  TextAttributesKey INLINED_VALUES_EXECUTION_LINE = TextAttributesKey.createTextAttributesKey("DEBUGGER_INLINED_VALUES_EXECUTION_LINE");
}
