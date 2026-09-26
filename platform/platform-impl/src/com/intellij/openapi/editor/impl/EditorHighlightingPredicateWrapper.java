// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl;

import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.util.Key;
import org.jetbrains.annotations.NotNull;

import java.util.function.Predicate;


final class EditorHighlightingPredicateWrapper implements EditorHighlightingPredicate {

  static final Key<EditorHighlightingPredicateWrapper> KEY = Key.create("default-highlighting-predicate");

  private final @NotNull Predicate<? super RangeHighlighter> filter;

  EditorHighlightingPredicateWrapper(@NotNull Predicate<? super RangeHighlighter> filter) {
    this.filter = filter;
  }

  @Override
  public boolean shouldRender(@NotNull RangeHighlighter highlighter) {
    return filter.test(highlighter);
  }
}
