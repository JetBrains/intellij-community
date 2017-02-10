/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.openapi.vcs;

import java.util.ArrayList;
import java.util.List;

/**
 * Created with IntelliJ IDEA.
 * User: Irina.Chernushina
 * Date: 1/25/12
 * Time: 5:03 PM
 */
public class LineHandlerHelper {
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
