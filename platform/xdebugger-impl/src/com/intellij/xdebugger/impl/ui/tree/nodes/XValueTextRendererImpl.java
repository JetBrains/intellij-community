/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.xdebugger.impl.ui.tree.nodes;

import com.intellij.openapi.editor.DefaultLanguageHighlighterColors;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.ui.ColoredTextContainer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.xdebugger.impl.ui.DebuggerUIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
* @author nik
*/
public class XValueTextRendererImpl extends XValueTextRendererBase {
  private final ColoredTextContainer myText;

  public XValueTextRendererImpl(@NotNull ColoredTextContainer text) {
    myText = text;
  }

  @Override
  public void renderValue(@NotNull String value) {
    XValuePresentationUtil.renderValue(value, myText, SimpleTextAttributes.REGULAR_ATTRIBUTES, -1, null);
  }

  @Override
  protected void renderRawValue(@NotNull String value, @NotNull TextAttributesKey key) {
    TextAttributes textAttributes = DebuggerUIUtil.getColorScheme().getAttributes(key);
    SimpleTextAttributes attributes = SimpleTextAttributes.fromTextAttributes(textAttributes);
    myText.append(value, attributes);
  }

  @Override
  public void renderStringValue(@NotNull String value, @Nullable String additionalSpecialCharsToHighlight, int maxLength) {
    TextAttributes textAttributes = DebuggerUIUtil.getColorScheme().getAttributes(DefaultLanguageHighlighterColors.STRING);
    SimpleTextAttributes attributes = SimpleTextAttributes.fromTextAttributes(textAttributes);
    myText.append("\"", attributes);
    XValuePresentationUtil.renderValue(value, myText, attributes, maxLength, additionalSpecialCharsToHighlight);
    myText.append("\"", attributes);
  }

  @Override
  public void renderComment(@NotNull String comment) {
    myText.append(comment, SimpleTextAttributes.GRAY_ATTRIBUTES);
  }

  @Override
  public void renderError(@NotNull String error) {
    myText.append(error, SimpleTextAttributes.ERROR_ATTRIBUTES);
  }

  @Override
  public void renderSpecialSymbol(@NotNull String symbol) {
    myText.append(symbol, SimpleTextAttributes.REGULAR_ATTRIBUTES);
  }
}
