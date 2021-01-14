// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class StatisticsEventEscaper {
  private static final String SYMBOLS_TO_REPLACE = ":;, ";
  private static final String SYMBOLS_TO_REPLACE_FIELD_NAME = '.' + SYMBOLS_TO_REPLACE;

  /**
   * Only printable ASCII symbols except whitespaces and '" are allowed.
   */
  @NotNull
  public static String escapeEventIdOrFieldValue(@NotNull String str) {
    return escapeInternal(str, null, true);
  }

  /**
   * Only printable ASCII symbols except whitespaces and :;,'" are allowed.
   */
  @NotNull
  public static String escape(@NotNull String str) {
    return escapeInternal(str, SYMBOLS_TO_REPLACE, false);
  }

  /**
   * Only printable ASCII symbols except whitespaces and .:;,'" are allowed.
   */
  @NotNull
  public static String escapeFieldName(@NotNull String str) {
    return escapeInternal(str, SYMBOLS_TO_REPLACE_FIELD_NAME, false);
  }

  /**
   * Removes symbols prohibited in 2020.2 or earlier versions but allowed in 2020.3+.
   * Used for backward compatibility with validation rules create before 2020.2.
   *
   * @return null if there are no prohibited symbols
   */
  @Nullable
  public static String cleanupForLegacyRulesIfNeeded(@NotNull String str) {
    if (containsSystemSymbols(str, SYMBOLS_TO_REPLACE)) {
      return replace(str, SYMBOLS_TO_REPLACE, false);
    }
    return null;
  }

  @NotNull
  private static String escapeInternal(@NotNull String str, @Nullable String toReplace, boolean allowSpaces) {
    if (containsSystemSymbols(str, toReplace)) {
      return replace(str, toReplace, allowSpaces);
    }
    return str;
  }

  @NotNull
  private static String replace(@NotNull String value, @Nullable String toReplace, boolean allowSpaces) {
    final StringBuilder out = new StringBuilder();
    for (int i = 0; i < value.length(); i++) {
      final char c = value.charAt(i);
      if (!isAscii(c)) {
        out.append("?");
      }
      else if (isWhiteSpaceToReplace(c)) {
        out.append(allowSpaces ? " " : "_");
      }
      else if (isSymbolToReplace(c, toReplace)) {
        out.append("_");
      }
      else if (!isProhibitedSymbol(c)) {
        out.append(c);
      }
    }
    return out.toString();
  }

  private static boolean containsSystemSymbols(@NotNull final String value, @Nullable String toReplace) {
    for (int i = 0; i < value.length(); i++) {
      final char c = value.charAt(i);
      if (!isAscii(c)) return true;
      if (isWhiteSpaceToReplace(c)) return true;
      if (isSymbolToReplace(c, toReplace)) return true;
      if (isProhibitedSymbol(c)) return true;
    }
    return false;
  }

  private static boolean isAscii(char c) {
    return c <= 127;
  }

  private static boolean isSymbolToReplace(char c, @Nullable String toReplace) {
    if (toReplace != null && containsChar(toReplace, c)) {
      return true;
    }
    return isAsciiControl(c);
  }

  public static boolean isWhiteSpaceToReplace(char c) {
    return c == '\n' || c == '\r' || c == '\t';
  }

  private static boolean isAsciiControl(char c) {
    return c < 32 || c == 127;
  }

  private static boolean isProhibitedSymbol(char c) {
    return c == '\'' || c == '"';
  }

  private static boolean containsChar(@NotNull String str, char c) {
    for (int i = 0; i < str.length(); i++) {
      if (str.charAt(i) == c) return true;
    }
    return false;
  }
}
