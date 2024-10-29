// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl;

import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.util.Key;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * A predicate for filtering out {@link RangeHighlighter highlighters} that should not be rendered in a given {@link EditorImpl editor}.
 *
 * @see EditorImpl#addHighlightingPredicate(Key, EditorHighlightingPredicate)
 */
@ApiStatus.Experimental
public interface EditorHighlightingPredicate {
  /**
   * checks that highlighter should be rendered in the corresponding editor
   */
  boolean shouldRender(@NotNull RangeHighlighter highlighter);
}
