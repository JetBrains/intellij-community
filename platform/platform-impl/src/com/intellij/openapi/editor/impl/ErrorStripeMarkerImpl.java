// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.impl;

import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.openapi.editor.ex.RangeHighlighterEx;
import com.intellij.openapi.util.Getter;
import org.jetbrains.annotations.NotNull;

class ErrorStripeMarkerImpl extends RangeMarkerImpl implements Getter<ErrorStripeMarkerImpl> {

  private final RangeHighlighterEx myHighlighter;

  ErrorStripeMarkerImpl(@NotNull DocumentEx document, @NotNull RangeHighlighterEx highlighter) {
    super(document, highlighter.getStartOffset(), highlighter.getEndOffset(), false, true);
    myHighlighter = highlighter;
  }

  @NotNull
  public RangeHighlighterEx getHighlighter() {
    return myHighlighter;
  }

  @Override
  public ErrorStripeMarkerImpl get() {
    return this;
  }

  @Override
  public boolean isValid() {
    return myHighlighter.isValid() && super.isValid();
  }
}
