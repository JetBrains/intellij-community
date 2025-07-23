// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl;

import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.ex.util.EmptyEditorHighlighter;
import com.intellij.openapi.editor.markup.TextAttributes;
import org.jetbrains.annotations.NotNull;


/**
 * use "null" editor highlighter (which doesn't provide any attributes) in editor by default (should resolve IDEA-171119)
 * <p>
 * there's no need to return attributes for HighlighterColors.TEXT key - they will be applied by IterationState anyway
 */
final class NullEditorHighlighter extends EmptyEditorHighlighter {

  private static final TextAttributes NULL_ATTRIBUTES = new TextAttributes();

  NullEditorHighlighter() {
    super(NULL_ATTRIBUTES);
  }

  @Override
  public void setColorScheme(@NotNull EditorColorsScheme scheme) {
  }
}
