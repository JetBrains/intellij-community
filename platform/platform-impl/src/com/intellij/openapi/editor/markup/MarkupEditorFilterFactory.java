// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.markup;

import com.intellij.openapi.diff.impl.DiffUtil;
import org.jetbrains.annotations.NotNull;

public final class MarkupEditorFilterFactory {
  private static final MarkupEditorFilter IS_DIFF_FILTER = editor -> DiffUtil.isDiffEditor(editor);
  private static final MarkupEditorFilter NOT_DIFF_FILTER = editor -> !DiffUtil.isDiffEditor(editor);

  public static @NotNull MarkupEditorFilter createNotFilter(@NotNull MarkupEditorFilter filter) {
    return editor -> !filter.avaliableIn(editor);
  }

  public static @NotNull MarkupEditorFilter createIsDiffFilter() {
    return IS_DIFF_FILTER;
  }

  public static @NotNull MarkupEditorFilter createIsNotDiffFilter() {
    return NOT_DIFF_FILTER;
  }
}
