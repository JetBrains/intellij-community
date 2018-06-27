// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.codeStyle;

import com.intellij.openapi.util.TextRange;
import org.jetbrains.annotations.NotNull;

public class Range extends TextRange {
  private final int myErrorCount;

  public Range(int startOffset, int endOffset, int errorCount) {
    super(startOffset, endOffset);
    myErrorCount = errorCount;
  }

  public int getErrorCount() {
    return myErrorCount;
  }

  @NotNull
  public Range shiftRight(int delta) {
    if (delta == 0) return this;
    return new Range(getStartOffset() + delta, getEndOffset() + delta, getErrorCount());
  }

  public static Range from(int from, int length) {
    return new Range(from, from + length, 0);
  }
}
