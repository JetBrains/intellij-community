// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public final class StatisticsEventEscaper {

  @NotNull
  public static String escape(@NotNull String str) {
    return escapeInternal(str, false);
  }

  @NotNull
  public static String escapeFieldName(@NotNull String str) {
    return escapeInternal(str, true);
  }

  @NotNull
  private static String escapeInternal(@NotNull String str, boolean replaceDot) {
    if (containsSystemSymbols(str, replaceDot)) {
      return replace(str, replaceDot);
    }
    return str;
  }

  @NotNull
  private static String replace(@NotNull final String value, boolean replaceDot) {
    final StringBuilder out = new StringBuilder();
    for (int i = 0; i < value.length(); i++) {
      final char c = value.charAt(i);
      if (!isAscii(c)) {
        out.append("?");
      }
      else if (isSymbolToReplace(c, replaceDot)) {
        out.append("_");
      }
      else if (!isProhibitedSymbol(c)) {
        out.append(c);
      }
    }
    return out.toString();
  }

  private static boolean containsSystemSymbols(@NotNull final String value, boolean replaceDot) {
    for (int i = 0; i < value.length(); i++) {
      final char c = value.charAt(i);
      if (!isAscii(c)) return true;
      if (isSymbolToReplace(c, replaceDot)) return true;
      if (isProhibitedSymbol(c)) return true;
    }
    return false;
  }

  private static boolean isAscii(char c) {
    return c <= 127;
  }

  private static boolean isSymbolToReplace(char c, boolean withDot) {
    if (withDot && c == '.') {
      return true;
    }
    return isAsciiControl(c) || isWhiteSpace(c) || c == ':' || c == ';' || c == ',';
  }

  public static boolean isWhiteSpace(char c) {
    return c == '\n' || c == '\t' || c == ' ';
  }

  private static boolean isAsciiControl(char c) {
    return c < 32 || c == 127;
  }

  private static boolean isProhibitedSymbol(char c) {
    return c == '\'' || c == '"';
  }
}
