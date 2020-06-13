// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs;

import java.util.ArrayList;
import java.util.List;

public final class LineHandlerHelper {
  /**
   * Split text into lines. New line characters are treated as separators. So if the text starts
   * with newline, empty string will be the first element, if the text ends with new line, the
   * empty string will be the last element. The returned lines will be substrings of
   * the text argument. The new line characters are included into the line text.
   *
   * @param text a text to split
   * @return a list of elements (note that there are always at least one element)
   */
  public static List<String> splitText(String text) {
    int startLine = 0;
    int i = 0;
    int n = text.length();
    ArrayList<String> rc = new ArrayList<>();
    while (i < n) {
      switch (text.charAt(i)) {
        case '\n':
          i++;
          if (i < n && text.charAt(i) == '\r') {
            i++;
          }
          rc.add(text.substring(startLine, i));
          startLine = i;
          break;
        case '\r':
          i++;
          if (i < n && text.charAt(i) == '\n') {
            i++;
          }
          rc.add(text.substring(startLine, i));
          startLine = i;
          break;
        default:
          i++;
      }
    }
    if (startLine == text.length()) {
      // still add empty line or previous line wouldn't be treated as completed
      rc.add("");
    } else {
      rc.add(text.substring(startLine, i));
    }
    return rc;
  }

  /**
   * Trim line separator from new line if it presents
   *
   * @param line a line to process
   * @return a trimmed line
   */
  public static String trimLineSeparator(String line) {
    int n = line.length();
    if (n == 0) {
      return line;
    }
    char ch = line.charAt(n - 1);
    if (ch == '\n' || ch == '\r') {
      n--;
    }
    else {
      return line;
    }
    if (n > 0) {
      char ch2 = line.charAt(n - 1);
      if ((ch2 == '\n' || ch2 == '\r') && ch2 != ch) {
        n--;
      }
    }
    return line.substring(0, n);

  }
}
