package com.intellij.grazie.utils;

import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
public final class NaturalTextDetector {
  public static boolean seemsNatural(CharSequence text) {
    return seemsNatural(text.toString());
  }

  public static boolean seemsNatural(String text) {
    int spaceCount = 0;
    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);
      if (c == ' ') {
        spaceCount++;
        if (spaceCount > 3) {
          return true;
        }
      }
      if (Character.getType(c) == Character.OTHER_LETTER) {
        return true;
      }
    }
    if (spaceCount < 1) {
      return false;
    }
    if (spaceCount == 1) {
      return text.chars().noneMatch(c -> Text.INSTANCE.isPunctuation((char) c));
    }
    return text.chars().allMatch(NaturalTextDetector::isExpectedInText);
  }

  private static boolean isExpectedInText(int c) {
    return Character.isWhitespace(c) || Character.isLetterOrDigit(c) || Text.INSTANCE.isPunctuation((char) c);
  }
}
