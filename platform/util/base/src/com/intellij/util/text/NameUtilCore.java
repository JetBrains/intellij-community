// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.text;

import com.intellij.openapi.util.text.StringUtilRt;
import com.intellij.util.ArrayUtilRt;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public final class NameUtilCore {

  private static final int KANA_START = 0x3040;
  private static final int KANA_END = 0x3358;
  private static final int KANA2_START = 0xFF66;
  private static final int KANA2_END = 0xFF9D;

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
    int ch = text.codePointAt(start);
    int chLen = Character.charCount(ch);
    if (!Character.isLetterOrDigit(ch)) {
      return start + chLen;
    }

    int i = start;
    while (i < text.length()) {
      int codePoint = text.codePointAt(i);
      if (!Character.isDigit(codePoint)) break;
      i += Character.charCount(codePoint);
    }
    if (i > start) {
      // digits form a separate hump
      return i;
    }

    while (i < text.length()) {
      int codePoint = text.codePointAt(i);
      if (!Character.isUpperCase(codePoint)) break;
      i += Character.charCount(codePoint);
    }

    if (i > start + chLen) {
      // several consecutive uppercase letter form a hump
      if (i == text.length() || !Character.isLetter(text.codePointAt(i))) {
        return i;
      }
      return i - Character.charCount(text.codePointBefore(i));
    }

    if (i == start) i += chLen;
    while (i < text.length()) {
      int codePoint = text.codePointAt(i);
      if (!Character.isLetter(codePoint) || isWordStart(text, i)) break;
      i += Character.charCount(codePoint);
    }
    return i;
  }

  public static boolean isWordStart(String text, int i) {
    int cur = text.codePointAt(i);
    int prev = i > 0 ? text.codePointBefore(i) : -1;
    if (Character.isUpperCase(cur)) {
      if (Character.isUpperCase(prev)) {
        // check that we're not in the middle of an all-caps word
        int nextPos = i + Character.charCount(cur);
        return nextPos < text.length() && Character.isLowerCase(text.codePointAt(nextPos));
      }
      return true;
    }
    if (Character.isDigit(cur)) {
      return true;
    }
    if (!Character.isLetter(cur)) {
      return false;
    }
    if (Character.isIdeographic(cur)) {
      // Consider every ideograph as a separate word
      return true;
    }
    return i == 0 || !Character.isLetterOrDigit(text.charAt(i - 1)) || isHardCodedWordStart(text, i) ||
           isKanaBreak(cur, prev);
  }
  
  private static boolean maybeKana(int codePoint) {
    return codePoint >= KANA_START && codePoint <= KANA_END ||
           codePoint >= KANA2_START && codePoint <= KANA2_END;
  }

  private static boolean isKanaBreak(int cur, int prev) {
    if (!maybeKana(cur) && !maybeKana(prev)) return false;
    Character.UnicodeScript curScript = Character.UnicodeScript.of(cur);
    Character.UnicodeScript prevScript = Character.UnicodeScript.of(prev);
    if (prevScript == curScript) return false;
    return (curScript == Character.UnicodeScript.KATAKANA || curScript == Character.UnicodeScript.HIRAGANA ||
            prevScript == Character.UnicodeScript.KATAKANA || prevScript == Character.UnicodeScript.HIRAGANA) &&
           prevScript != Character.UnicodeScript.COMMON && curScript != Character.UnicodeScript.COMMON;
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
