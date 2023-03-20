// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.tools.util.text;

public interface LineOffsets {
  int getLineStart(int line);

  /**
   * includeNewline = false
   */
  int getLineEnd(int line);

  int getLineEnd(int line, boolean includeNewline);

  int getLineNumber(int offset);

  int getLineCount();

  int getTextLength();
}
