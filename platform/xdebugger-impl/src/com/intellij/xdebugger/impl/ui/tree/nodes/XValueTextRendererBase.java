// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xdebugger.impl.ui.tree.nodes;

import com.intellij.openapi.editor.DefaultLanguageHighlighterColors;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.xdebugger.frame.presentation.XValuePresentation;
import org.jetbrains.annotations.NotNull;

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

  protected abstract void renderRawValue(@NotNull @NlsSafe String value, @NotNull TextAttributesKey key);
}
