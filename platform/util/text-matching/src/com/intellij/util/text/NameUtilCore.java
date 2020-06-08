// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.text;

import com.intellij.openapi.util.text.StringUtilRt;
import com.intellij.util.ArrayUtilRt;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public final class NameUtilCore {
  /**
   * Splits an identifier into words, separated with underscores or upper-case characters
   * (camel-case).
   *
   * @param name the identifier to split.
   * @return the array of strings into which the identifier has been split.
   */
  public static String @NotNull [] splitNameIntoWords(@NotNull String name) {
    final String[] underlineDelimited = name.split("_");
    List<String> result = new ArrayList<>();
    for (String word : underlineDelimited) {
      addAllWords(word, result);
    }
    return ArrayUtilRt.toStringArray(result);
  }

  private static void addAllWords(@NotNull String text, @NotNull List<? super String> result) {
    int start = 0;
    while (start < text.length()) {
      int next = nextWord(text, start);
      result.add(text.substring(start, next));
      start = next;
    }
  }

  public static int nextWord(@NotNull String text, int start) {
    if (!Character.isLetterOrDigit(text.charAt(start))) {
      return start + 1;
    }

    int i = start;
    while (i < text.length() && Character.isDigit(text.charAt(i))) i++;
    if (i > start) {
      // digits form a separate hump
      return i;
    }

    while (i < text.length() && Character.isUpperCase(text.charAt(i))) i++;

    if (i > start + 1) {
      // several consecutive uppercase letter form a hump
      if (i == text.length() || !Character.isLetter(text.charAt(i))) {
        return i;
      }
      return i - 1;
    }

    if (i == start) i++;
    while (i < text.length() && Character.isLetter(text.charAt(i)) && !isWordStart(text, i)) i++;
    return i;
  }

  public static boolean isWordStart(String text, int i) {
    char c = text.charAt(i);
    if (Character.isUpperCase(c)) {
      if (i > 0 && Character.isUpperCase(text.charAt(i - 1))) {
        // check that we're not in the middle of an all-caps word
        return i + 1 < text.length() && Character.isLowerCase(text.charAt(i + 1));
      }
      return true;
    }
    if (Character.isDigit(c)) {
      return true;
    }
    if (!Character.isLetter(c)) {
      return false;
    }
    return i == 0 || !Character.isLetterOrDigit(text.charAt(i - 1)) || isHardCodedWordStart(text, i);
  }

  private static boolean isHardCodedWordStart(String text, int i) {
    return text.charAt(i) == 'l' &&
           i < text.length() - 1 && text.charAt(i + 1) == 'n' &&
           (text.length() == i + 2 || isWordStart(text, i + 2));
  }

  public static String @NotNull [] nameToWords(@NotNull String name) {
    List<String> array = new ArrayList<>();
    int index = 0;

    while (index < name.length()) {
      int wordStart = index;
      int upperCaseCount = 0;
      int lowerCaseCount = 0;
      int digitCount = 0;
      int specialCount = 0;
      while (index < name.length()) {
        char c = name.charAt(index);
        if (Character.isDigit(c)) {
          if (upperCaseCount > 0 || lowerCaseCount > 0 || specialCount > 0) break;
          digitCount++;
        }
        else if (Character.isUpperCase(c)) {
          if (lowerCaseCount > 0 || digitCount > 0 || specialCount > 0) break;
          upperCaseCount++;
        }
        else if (Character.isLowerCase(c)) {
          if (digitCount > 0 || specialCount > 0) break;
          if (upperCaseCount > 1) {
            index--;
            break;
          }
          lowerCaseCount++;
        }
        else {
          if (upperCaseCount > 0 || lowerCaseCount > 0 || digitCount > 0) break;
          specialCount++;
        }
        index++;
      }
      String word = name.substring(wordStart, index);
      if (!StringUtilRt.isEmptyOrSpaces(word)) {
        array.add(word);
      }
    }
    return ArrayUtilRt.toStringArray(array);
  }
}
