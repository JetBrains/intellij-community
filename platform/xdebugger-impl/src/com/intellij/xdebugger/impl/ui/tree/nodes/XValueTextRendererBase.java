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
package com.intellij.xdebugger.impl.ui.tree.nodes;

import com.intellij.openapi.editor.DefaultLanguageHighlighterColors;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.xdebugger.frame.presentation.XValuePresentation;
import org.jetbrains.annotations.NotNull;

/**
 * @author nik
 */
public abstract class XValueTextRendererBase implements XValuePresentation.XValueTextRenderer {
  @Override
  public void renderStringValue(@NotNull String value) {
    renderStringValue(value, null, -1);
  }

  @Override
  public void renderNumericValue(@NotNull String value) {
    renderRawValue(value, DefaultLanguageHighlighterColors.NUMBER);
  }

  @Override
  public void renderKeywordValue(@NotNull String value) {
    renderRawValue(value, DefaultLanguageHighlighterColors.KEYWORD);
  }

  @Override
  public final void renderValue(@NotNull String value, @NotNull TextAttributesKey key) {
    renderRawValue(value, key);
  }

  protected abstract void renderRawValue(@NotNull String value, @NotNull TextAttributesKey key);
}
