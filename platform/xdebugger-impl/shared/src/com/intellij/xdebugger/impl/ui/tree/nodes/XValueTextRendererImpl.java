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

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors;
import com.intellij.openapi.editor.colors.EditorColorsUtil;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.ui.ColoredTextContainer;
import com.intellij.ui.SimpleTextAttributes;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class XValueTextRendererImpl extends XValueTextRendererBase implements XCustomizableTextRenderer {
  private final ColoredTextContainer myText;

  public XValueTextRendererImpl(@NotNull ColoredTextContainer text) {
    myText = text;
  }

  @Override
  public void renderValue(@NotNull String value) {
    XValuePresentationUtil.renderValue(value, myText, SimpleTextAttributes.REGULAR_ATTRIBUTES, -1, null,
                                       getAttributes(DefaultLanguageHighlighterColors.VALID_STRING_ESCAPE));
  }

  @Override
  protected void renderRawValue(@NotNull String value, @NotNull TextAttributesKey key) {
    TextAttributes textAttributes = getAttributes(key);
    SimpleTextAttributes attributes = SimpleTextAttributes.fromTextAttributes(textAttributes);
    myText.append(value, attributes);
  }

  @Override
  public void renderStringValue(@NotNull String value, @Nullable String additionalSpecialCharsToHighlight, int maxLength) {
    TextAttributes textAttributes = getAttributes(DefaultLanguageHighlighterColors.STRING);
    SimpleTextAttributes attributes = SimpleTextAttributes.fromTextAttributes(textAttributes);
    myText.append("\"", attributes);
    XValuePresentationUtil.renderValue(value, myText, attributes, maxLength, additionalSpecialCharsToHighlight,
                                       getAttributes(DefaultLanguageHighlighterColors.VALID_STRING_ESCAPE));
    myText.append("\"", attributes);
  }

  private static @Nullable TextAttributes getAttributes(@NotNull TextAttributesKey key) {
    var application = ApplicationManager.getApplication();
    // ex. in DAP there is no EditorColorsManager
    if (application == null || application.isHeadlessEnvironment()) {
      return key.getDefaultAttributes();
    }
    return EditorColorsUtil.getGlobalOrDefaultColorScheme().getAttributes(key);
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

  @ApiStatus.Internal
  @Override
  public void renderRaw(@NotNull @NlsSafe String text, @NotNull SimpleTextAttributes attributes) {
    myText.append(text, attributes);
  }
}
