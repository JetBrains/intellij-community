// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.stickyLines;

import com.intellij.openapi.util.TextRange;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static org.jetbrains.annotations.ApiStatus.Internal;

/**
 * Represents information about sticky line collected by {@link StickyLinesCollector}.
 * <p>
 * See also {@link StickyLine}
 */
@Internal
public record StickyLineInfo(int textOffset, int endOffset, @Nullable String debugText) {

  StickyLineInfo(@NotNull TextRange textRange) {
    this(textRange.getStartOffset(), textRange.getEndOffset(), null);
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) return true;
    if (!(other instanceof StickyLineInfo info)) return false;
    return endOffset == info.endOffset && textOffset == info.textOffset;
  }

  @Override
  public int hashCode() {
    return textOffset + 31 * endOffset;
  }
}
