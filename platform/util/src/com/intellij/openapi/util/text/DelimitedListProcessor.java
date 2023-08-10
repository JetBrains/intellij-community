// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.util.text;

import org.jetbrains.annotations.NotNull;

/**
 * @author Dmitry Avdeev
 */
public abstract class DelimitedListProcessor {

  private final String myDelimiters;

  public DelimitedListProcessor(final String delimiters) {
    myDelimiters = delimiters;
  }

  public void processText(final @NotNull String text) {
    int start;
    int pos = 0;

    do {
      start = pos;
      pos = skipDelimiters(text, pos);
      if (pos == text.length()) {
        processToken(start, pos, true);
        break;
      }
      start = pos;
      while (++pos < text.length() && !isDelimiter(text.charAt(pos))) {}
      processToken(start, pos, false);
      pos++;
    } while(pos < text.length());

  }

  protected abstract void processToken(final int start, final int end, final boolean delimitersOnly);

  protected int skipDelimiters(String s, int pos) {
    while (pos < s.length()) {
      final char ch = s.charAt(pos);
      if (!isDelimiter(ch)) {
        break;
      }
      pos++;
    }
    return pos;
  }

  protected boolean isDelimiter(char ch) {
    return ch < ' ' || myDelimiters.indexOf(ch) != -1;
  }
}
