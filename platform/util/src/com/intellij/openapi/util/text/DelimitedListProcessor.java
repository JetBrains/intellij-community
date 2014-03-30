/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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

  public void processText(@NotNull final String text) {
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
