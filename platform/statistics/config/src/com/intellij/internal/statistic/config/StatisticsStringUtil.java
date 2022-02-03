// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.config;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class StatisticsStringUtil {
  @Contract(value = "null -> true", pure = true)
  public static boolean isEmpty(@Nullable String s) {
    return s == null || s.isEmpty();
  }

  @Contract(value = "null -> false",pure = true)
  public static boolean isNotEmpty(@Nullable String s) {
    return !isEmpty(s);
  }

  @Contract(value = "null -> true", pure = true)
  public static boolean isEmptyOrSpaces(@Nullable String s) {
    if (isEmpty(s)) {
      return true;
    }
    for (int i = 0; i < s.length(); i++) {
      if (s.charAt(i) > ' ') {
        return false;
      }
    }
    return true;
  }

  public static boolean equals(@Nullable String s1, @Nullable String s2) {
    if (s1 == s2) return true;
    if (s1 == null || s2 == null) return false;

    if (s1.length() != s2.length()) return false;
    return s1.equals(s2);
  }

  @NotNull
  public static List<String> split(@NotNull String text, char separator) {
    List<String> result = new ArrayList<>();
    int pos = 0;
    int index = text.indexOf(separator, pos);
    while (index >= 0) {
      final int nextPos = index + 1;
      String token = text.substring(pos, index);
      if (token.length() != 0) {
        result.add(token);
      }
      pos = nextPos;
      index = text.indexOf(separator, pos);
    }

    if (pos < text.length()) {
      result.add(text.substring(pos));
    }
    return result;
  }

  @Contract(value = "null -> null; !null -> !null", pure = true)
  public static String toLowerCase(@Nullable String str) {
    return str == null ? null : str.toLowerCase(Locale.ENGLISH);
  }
}
