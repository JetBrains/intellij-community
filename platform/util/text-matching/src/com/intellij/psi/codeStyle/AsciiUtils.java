// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.codeStyle;

import org.jetbrains.annotations.NotNull;

/**
 * String utility methods that assume that the string contents is ASCII-only (all codepoints are <= 127).
 * These methods may work faster but work incorrectly on other characters
 */
final class AsciiUtils {
  /**
   * Implementation of {@link com.intellij.util.text.NameUtilCore#nextWord(String, int)} for ASCII-only strings
   * 
   * @param text text to find next word in
   * @param start starting position within the text
   * @return position of the next word; may point to the end of the string
   */
  static int nextWordAscii(@NotNull String text, int start) {
    if (!isLetterOrDigitAscii(text.charAt(start))) {
      return start + 1;
    }

    int i = start;
    while (i < text.length() && isDigitAscii(text.charAt(i))) {
      i++;
    }
    if (i > start) {
      // digits form a separate hump
      return i;
    }

    while (i < text.length() && isUpperAscii(text.charAt(i))) {
      i++;
    }

    if (i > start + 1) {
      // several consecutive uppercase letter form a hump
      if (i == text.length() || !isLetterAscii(text.charAt(i))) {
        return i;
      }
      return i - 1;
    }

    if (i == start) i += 1;
    while (i < text.length() && isLetterAscii(text.charAt(i)) && !isWordStartAscii(text, i)) {
      i++;
    }
    return i;
  }

  private static boolean isWordStartAscii(String text, int i) {
    char cur = text.charAt(i);
    char prev = i > 0 ? text.charAt(i - 1) : 0;
    if (isUpperAscii(cur)) {
      if (isUpperAscii(prev)) {
        // check that we're not in the middle of an all-caps word
        int nextPos = i + 1;
        if (nextPos >= text.length()) return false;
        return isLowerAscii(text.charAt(nextPos));
      }
      return true;
    }
    if (isDigitAscii(cur)) {
      return true;
    }
    if (!isLetterAscii(cur)) {
      return false;
    }
    return i == 0 || !isLetterOrDigitAscii(text.charAt(i - 1));
  }

  private static boolean isLetterAscii(char cur) {
    return cur >= 'a' && cur <= 'z' || cur >= 'A' && cur <= 'Z';
  }

  private static boolean isLetterOrDigitAscii(char cur) {
    return isLetterAscii(cur) || isDigitAscii(cur);
  }

  private static boolean isDigitAscii(char cur) {
    return cur >= '0' && cur <= '9';
  }

  static char toUpperAscii(char c) {
    if (c >= 'a' && c <= 'z') {
      return (char)(c + ('A' - 'a'));
    }
    return c;
  }

  static char toLowerAscii(char c) {
    if (c >= 'A' && c <= 'Z') {
      return (char)(c - ('A' - 'a'));
    }
    return c;
  }

  static boolean isUpperAscii(char c) {
    return 'A' <= c && c <= 'Z';
  }

  static boolean isLowerAscii(char c) {
    return 'a' <= c && c <= 'z';
  }

  /**
   * @param string string to check
   * @return true if a given string contains ASCII-only characters, so it can be processed with other methods in this class
   */
  static boolean isAscii(@NotNull String string) {
    int length = string.length();
    for (int i = 0; i < length; ++i) {
      char c = string.charAt(i);
      if (c >= 128) {
        return false;
      }
    }
    return true;
  }
}
