// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog.util;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ValidatorStringUtil {
  private ValidatorStringUtil() {
  }

  @Contract(pure = true)
  public static @NotNull String replace(@NotNull String text, @NotNull List<String> from, @NotNull List<String> to) {
    assert from.size() == to.size();
    StringBuilder result = null;
    replace:
    for (int i = 0; i < text.length(); i++) {
      for (int j = 0; j < from.size(); j += 1) {
        String toReplace = from.get(j);
        String replaceWith = to.get(j);

        final int len = toReplace.length();
        if (len == 0) continue;
        if (text.regionMatches(i, toReplace, 0, len)) {
          if (result == null) {
            result = new StringBuilder(text.length());
            result.append(text, 0, i);
          }
          result.append(replaceWith);
          //noinspection AssignmentToForLoopParameter
          i += len - 1;
          continue replace;
        }
      }

      if (result != null) {
        result.append(text.charAt(i));
      }
    }
    return result == null ? text : result.toString();
  }

  @Contract(value = "null -> true", pure = true)
  public static boolean isEmpty(@Nullable String s) {
    return s == null || s.isEmpty();
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

  public static @NotNull List<String> split(@NotNull String s, @NotNull String separator, boolean excludeSeparator, boolean excludeEmptyStrings) {
    //noinspection unchecked
    return (List)split((CharSequence)s, separator, excludeSeparator, excludeEmptyStrings);
  }

  @Contract(pure = true)
  public static @NotNull List<CharSequence> split(@NotNull CharSequence s, @NotNull CharSequence separator, boolean excludeSeparator, boolean excludeEmptyStrings) {
    if (separator.length() == 0) {
      return Collections.singletonList(s);
    }
    List<CharSequence> result = new ArrayList<>();
    int pos = 0;
    while (true) {
      int index = indexOf(s, separator, pos, s.length());
      if (index == -1) break;
      final int nextPos = index + separator.length();
      CharSequence token = s.subSequence(pos, excludeSeparator ? index : nextPos);
      if (token.length() != 0 || !excludeEmptyStrings) {
        result.add(token);
      }
      pos = nextPos;
    }
    if (pos < s.length() || !excludeEmptyStrings && pos == s.length()) {
      result.add(s.subSequence(pos, s.length()));
    }
    return result;
  }

  @Contract(pure = true)
  public static int indexOf(@NotNull CharSequence sequence, @NotNull CharSequence infix, int start, int end) {
    for (int i = start; i <= end - infix.length(); i++) {
      if (startsWith(sequence, i, infix)) {
        return i;
      }
    }
    return -1;
  }

  @Contract(pure = true)
  public static boolean startsWith(@NotNull CharSequence text, int startIndex, @NotNull CharSequence prefix) {
    int tl = text.length();
    if (startIndex < 0 || startIndex > tl) {
      throw new IllegalArgumentException("Index is out of bounds: " + startIndex + ", length: " + tl);
    }
    int l1 = tl - startIndex;
    int l2 = prefix.length();
    if (l1 < l2) return false;

    for (int i = 0; i < l2; i++) {
      if (text.charAt(i + startIndex) != prefix.charAt(i)) return false;
    }
    return true;
  }

}
