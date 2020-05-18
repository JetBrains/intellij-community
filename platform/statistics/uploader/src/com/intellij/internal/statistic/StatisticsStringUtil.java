// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class StatisticsStringUtil {
  public static boolean isEmpty(@Nullable String s) {
    return s == null || s.isEmpty();
  }

  public static boolean isNotEmpty(@Nullable String s) {
    return !isEmpty(s);
  }

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
}
