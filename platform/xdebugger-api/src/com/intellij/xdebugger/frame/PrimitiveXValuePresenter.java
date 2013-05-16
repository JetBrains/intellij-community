/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.xdebugger.frame;

import com.intellij.openapi.editor.DefaultLanguageHighlighterColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.ui.SimpleColoredText;
import com.intellij.ui.SimpleTextAttributes;

public class PrimitiveXValuePresenter implements XValuePresenter {
  public static final XValuePresenter KEYWORD = new PrimitiveXValuePresenter(DefaultLanguageHighlighterColors.KEYWORD);
  public static final XValuePresenter NUMBER = new PrimitiveXValuePresenter(DefaultLanguageHighlighterColors.NUMBER);

  private final TextAttributesKey textAttributesKey;

  public PrimitiveXValuePresenter(TextAttributesKey textAttributesKey) {
    this.textAttributesKey = textAttributesKey;
  }

  @Override
  public void append(String value, SimpleColoredText text, boolean changed) {
    text.append(value, SimpleTextAttributes.fromTextAttributes(EditorColorsManager.getInstance().getGlobalScheme().getAttributes(textAttributesKey)));
  }
}