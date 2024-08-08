// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.structuralsearch;

import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;

import java.text.Normalizer;
import java.util.regex.Pattern;

/**
 * @author Bas Leijdekkers
 */
public final class MatchUtil {
  private static final String REG_EXP_META_CHARS = ".$|()[]{}^?*+\\";
  private static final Pattern ACCENTS = Pattern.compile("\\p{InCombiningDiacriticalMarks}+");

  public static boolean containsRegExpMetaChar(String s) {
    return s.chars().anyMatch(MatchUtil::isRegExpMetaChar);
  }

  public static boolean isRegExpMetaChar(int ch) {
    return REG_EXP_META_CHARS.indexOf(ch) >= 0;
  }

  @NotNull
  public static String shieldRegExpMetaChars(@NotNull String word) {
    return shieldRegExpMetaChars(word, new StringBuilder(word.length())).toString();
  }

  public static String makeExtremeSpacesOptional(String word) {
    if (word.trim().isEmpty()) return word;

    String result = word;
    if (word.startsWith(" ")) result = "(?:\\s|\\b)" + result.substring(1);
    if (word.endsWith(" ")) result = result.substring(0, result.length() - 1) + "(?:\\s|\\b)";
    return result;
  }

  @NotNull
  public static StringBuilder shieldRegExpMetaChars(String word, StringBuilder out) {
    for (int i = 0, length = word.length(); i < length; ++i) {
      if (isRegExpMetaChar(word.charAt(i))) {
        out.append("\\");
      }
      out.append(word.charAt(i));
    }

    return out;
  }

  public static Pattern[] createPatterns(String[] prefixes) {
    final Pattern[] patterns = new Pattern[prefixes.length];

    for (int i = 0; i < prefixes.length; i++) {
      final String s = shieldRegExpMetaChars(prefixes[i]);
      patterns[i] = Pattern.compile("\\b(" + s + "\\w+)\\b");
    }
    return patterns;
  }

  @NotNull
  public static String normalizeWhiteSpace(@NotNull String text) {
    text = text.trim();
    final StringBuilder result = new StringBuilder();
    boolean white = false;
    for (int i = 0, length = text.length(); i < length; i++) {
      final char c = text.charAt(i);
      if (StringUtil.isWhiteSpace(c)) {
        if (!white) {
          result.append(' ');
          white = true;
        }
      }
      else {
        white = false;
        result.append(c);
      }
    }
    return result.toString();
  }

  @NotNull
  public static String stripAccents(@NotNull String input) {
    return ACCENTS.matcher(Normalizer.normalize(input, Normalizer.Form.NFD)).replaceAll("");
  }

  @NotNull
  public static String normalize(@NotNull String text) {
    return stripAccents(normalizeWhiteSpace(text));
  }
}
