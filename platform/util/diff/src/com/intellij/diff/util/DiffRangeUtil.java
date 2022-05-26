// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.util;

import com.intellij.diff.tools.util.text.LineOffsets;
import com.intellij.openapi.util.TextRange;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class DiffRangeUtil {
  @NotNull
  public static CharSequence getLinesContent(@NotNull CharSequence sequence, @NotNull LineOffsets lineOffsets, int line1, int line2) {
    return getLinesContent(sequence, lineOffsets, line1, line2, false);
  }

  @NotNull
  public static CharSequence getLinesContent(@NotNull CharSequence sequence, @NotNull LineOffsets lineOffsets, int line1, int line2,
                                             boolean includeNewline) {
    assert sequence.length() == lineOffsets.getTextLength();
    return getLinesRange(lineOffsets, line1, line2, includeNewline).subSequence(sequence);
  }

  @NotNull
  public static TextRange getLinesRange(@NotNull LineOffsets lineOffsets, int line1, int line2, boolean includeNewline) {
    if (line1 == line2) {
      int lineStartOffset = line1 < lineOffsets.getLineCount() ? lineOffsets.getLineStart(line1) : lineOffsets.getTextLength();
      return new TextRange(lineStartOffset, lineStartOffset);
    }
    else {
      int startOffset = lineOffsets.getLineStart(line1);
      int endOffset = lineOffsets.getLineEnd(line2 - 1);
      if (includeNewline && endOffset < lineOffsets.getTextLength()) endOffset++;
      return new TextRange(startOffset, endOffset);
    }
  }


  @NotNull
  public static List<String> getLines(@NotNull CharSequence text, @NonNls LineOffsets lineOffsets) {
    return getLines(text, lineOffsets, 0, lineOffsets.getLineCount());
  }

  @NotNull
  public static List<String> getLines(@NotNull CharSequence text, @NonNls LineOffsets lineOffsets, int startLine, int endLine) {
    if (startLine < 0 || startLine > endLine || endLine > lineOffsets.getLineCount()) {
      throw new IndexOutOfBoundsException(String.format("Wrong line range: [%d, %d); lineCount: '%d'",
                                                        startLine, endLine, lineOffsets.getLineCount()));
    }

    List<String> result = new ArrayList<>();
    for (int i = startLine; i < endLine; i++) {
      int start = lineOffsets.getLineStart(i);
      int end = lineOffsets.getLineEnd(i);
      result.add(text.subSequence(start, end).toString());
    }
    return result;
  }
}
