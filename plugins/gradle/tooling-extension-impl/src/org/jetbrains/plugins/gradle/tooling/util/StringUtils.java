// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.tooling.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class StringUtils {
  private static final Pattern WORD_SEPARATOR = Pattern.compile("\\W+");

  /**
   * Decompiled from org.gradle.util.GUtil
   */
  public static String toCamelCase(String string, boolean lower) {
    if (string == null) {
      return null;
    } else {
      StringBuilder builder = new StringBuilder();
      Matcher matcher = WORD_SEPARATOR.matcher(string);
      int pos = 0;
      boolean first = true;

      while(true) {
        String chunk;
        do {
          if (!matcher.find()) {
            chunk = string.subSequence(pos, string.length()).toString();
            if (lower && first) {
              chunk = org.apache.commons.lang3.StringUtils.uncapitalize(chunk);
            } else {
              chunk = org.apache.commons.lang3.StringUtils.capitalize(chunk);
            }

            builder.append(chunk);
            return builder.toString();
          }

          chunk = string.subSequence(pos, matcher.start()).toString();
          pos = matcher.end();
        } while(chunk.isEmpty());

        if (lower && first) {
          chunk = org.apache.commons.lang3.StringUtils.uncapitalize(chunk);
          first = false;
        } else {
          chunk = org.apache.commons.lang3.StringUtils.capitalize(chunk);
        }

        builder.append(chunk);
      }
    }
  }

  /**
   * Same functionality as `com.intellij.openapi.util.text.StringUtil#commonPrefixLength`
   */
  public static int commonPrefixLength(CharSequence s1, CharSequence s2) {
    int maxCommonLength = Math.min(s1.length(), s2.length());
    for (int i = 0; i < maxCommonLength; ++i) {
      if (s1.charAt(i) != s2.charAt(i)) {
        return i;
      }
    }
    return maxCommonLength;
  }

  /**
   * Same functionality as `com.intellij.openapi.util.text.StringUtil#commonSuffixLength`
   */
  public static int commonSuffixLength(CharSequence s1, CharSequence s2) {
    int s1Length = s1.length();
    int s2Length = s2.length();
    int maxCommonLength = Math.min(s1Length, s2Length);
    for (int i = 0; i < maxCommonLength; ++i) {
      if (s1.charAt(s1Length - 1 - i) != s2.charAt(s2Length - 1 - i)) {
        return i;
      }
    }
    return maxCommonLength;
  }
}
