// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.util.text;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;

/**
 * Stripped-down version of {@link com.intellij.openapi.util.text.StringUtil}.
 * Intended to use by external (out-of-IDE-process) runners and helpers so it should not contain any library dependencies.
 */
public class StringUtilRt {
  @Contract("null,!null,_ -> false; !null,null,_ -> false; null,null,_ -> true")
  public static boolean equal(@Nullable CharSequence s1, @Nullable CharSequence s2, boolean caseSensitive) {
    if (s1 == s2) return true;
    if (s1 == null || s2 == null) return false;

    if (s1.length() != s2.length()) return false;

    if (caseSensitive) {
      for (int i = 0; i < s1.length(); i++) {
        if (s1.charAt(i) != s2.charAt(i)) {
          return false;
        }
      }
    }
    else {
      for (int i = 0; i < s1.length(); i++) {
        if (!charsEqualIgnoreCase(s1.charAt(i), s2.charAt(i))) {
          return false;
        }
      }
    }

    return true;
  }

  @Contract(pure = true)
  public static boolean charsEqualIgnoreCase(char a, char b) {
    return a == b || toUpperCase(a) == toUpperCase(b) || toLowerCase(a) == toLowerCase(b);
  }

  @Contract(pure = true)
  public static char toUpperCase(char a) {
    if (a < 'a') return a;
    if (a <= 'z') return (char)(a + ('A' - 'a'));
    return Character.toUpperCase(a);
  }

  @Contract(pure = true)
  public static char toLowerCase(char a) {
    if (a <= 'z') {
      return a >= 'A' && a <= 'Z' ? (char)(a + ('a' - 'A')) : a;
    }
    return Character.toLowerCase(a);
  }

  /**
   * Converts line separators to {@code "\n"}
   */
  @NotNull
  @Contract(pure = true)
  public static String convertLineSeparators(@NotNull String text) {
    return convertLineSeparators(text, false);
  }

  @NotNull
  @Contract(pure = true)
  public static String convertLineSeparators(@NotNull String text, boolean keepCarriageReturn) {
    return convertLineSeparators(text, "\n", null, keepCarriageReturn);
  }

  @NotNull
  @Contract(pure = true)
  public static String convertLineSeparators(@NotNull String text, @NotNull String newSeparator) {
    return convertLineSeparators(text, newSeparator, null);
  }

  @NotNull
  @Contract(pure = true)
  public static CharSequence convertLineSeparators(@NotNull CharSequence text, @NotNull String newSeparator) {
    return unifyLineSeparators(text, newSeparator, null, false);
  }

  @NotNull
  public static String convertLineSeparators(@NotNull String text, @NotNull String newSeparator, @Nullable int[] offsetsToKeep) {
    return convertLineSeparators(text, newSeparator, offsetsToKeep, false);
  }

  @NotNull
  public static String convertLineSeparators(@NotNull String text,
                                             @NotNull String newSeparator,
                                             @Nullable int[] offsetsToKeep,
                                             boolean keepCarriageReturn) {
    return unifyLineSeparators(text, newSeparator, offsetsToKeep, keepCarriageReturn).toString();
  }

  @NotNull
  private static CharSequence unifyLineSeparators(@NotNull CharSequence text,
                                                  @NotNull String newSeparator,
                                                  @Nullable int[] offsetsToKeep,
                                                  boolean keepCarriageReturn) {
    StringBuilder buffer = null;
    int intactLength = 0;
    boolean newSeparatorIsSlashN = "\n".equals(newSeparator);
    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);
      if (c == '\n') {
        if (!newSeparatorIsSlashN) {
          if (buffer == null) {
            buffer = new StringBuilder(text.length());
            buffer.append(text, 0, intactLength);
          }
          buffer.append(newSeparator);
          shiftOffsets(offsetsToKeep, buffer.length(), 1, newSeparator.length());
        }
        else if (buffer == null) {
          intactLength++;
        }
        else {
          buffer.append('\n');
        }
      }
      else if (c == '\r') {
        boolean followedByLineFeed = i < text.length() - 1 && text.charAt(i + 1) == '\n';
        if (!followedByLineFeed && keepCarriageReturn) {
          if (buffer == null) {
            intactLength++;
          }
          else {
            buffer.append('\r');
          }
          continue;
        }
        if (buffer == null) {
          buffer = new StringBuilder(text.length());
          buffer.append(text, 0, intactLength);
        }
        buffer.append(newSeparator);
        if (followedByLineFeed) {
          //noinspection AssignmentToForLoopParameter
          i++;
          shiftOffsets(offsetsToKeep, buffer.length(), 2, newSeparator.length());
        }
        else {
          shiftOffsets(offsetsToKeep, buffer.length(), 1, newSeparator.length());
        }
      }
      else {
        if (buffer == null) {
          intactLength++;
        }
        else {
          buffer.append(c);
        }
      }
    }
    return buffer == null ? text : buffer;
  }

  private static void shiftOffsets(int[] offsets, int changeOffset, int oldLength, int newLength) {
    if (offsets == null) return;
    int shift = newLength - oldLength;
    if (shift == 0) return;
    for (int i = 0; i < offsets.length; i++) {
      int offset = offsets[i];
      if (offset >= changeOffset + oldLength) {
        offsets[i] += shift;
      }
    }
  }

  @Contract(pure = true)
  public static int parseInt(@Nullable String string, int defaultValue) {
    if (string != null) {
      try { return Integer.parseInt(string); }
      catch (NumberFormatException ignored) { }
    }
    return defaultValue;
  }

  @Contract(pure = true)
  public static long parseLong(@Nullable String string, long defaultValue) {
    if (string != null) {
      try { return Long.parseLong(string); }
      catch (NumberFormatException ignored) { }
    }
    return defaultValue;
  }

  @Contract(pure = true)
  public static double parseDouble(@Nullable String string, double defaultValue) {
    if (string != null) {
      try { return Double.parseDouble(string); }
      catch (NumberFormatException ignored) { }
    }
    return defaultValue;
  }

  @Contract(pure = true)
  static <E extends Enum<E>> E parseEnum(@NotNull String string, E defaultValue, @NotNull Class<E> clazz) {
    try {
      return Enum.valueOf(clazz, string);
    }
    catch (Exception e) {
      return defaultValue;
    }
  }

  @NotNull
  @Contract(pure = true)
  public static String getShortName(@NotNull Class<?> aClass) {
    return getShortName(aClass.getName());
  }

  @NotNull
  @Contract(pure = true)
  public static String getShortName(@NotNull String fqName) {
    return getShortName(fqName, '.');
  }

  @NotNull
  @Contract(pure = true)
  public static String getShortName(@NotNull String fqName, char separator) {
    int lastPointIdx = fqName.lastIndexOf(separator);
    if (lastPointIdx >= 0) {
      return fqName.substring(lastPointIdx + 1);
    }
    return fqName;
  }

  @Contract(pure = true)
  public static boolean endsWithChar(@Nullable CharSequence s, char suffix) {
    return s != null && s.length() != 0 && s.charAt(s.length() - 1) == suffix;
  }

  @Contract(pure = true)
  public static boolean endsWith(@NotNull CharSequence text, @NotNull CharSequence suffix) {
    int l1 = text.length();
    int l2 = suffix.length();
    if (l1 < l2) return false;

    for (int i = l1 - 1; i >= l1 - l2; i--) {
      if (text.charAt(i) != suffix.charAt(i + l2 - l1)) return false;
    }

    return true;
  }

  @Contract(pure = true)
  public static boolean startsWithIgnoreCase(@NotNull String str, @NotNull String prefix) {
    int stringLength = str.length();
    int prefixLength = prefix.length();
    return stringLength >= prefixLength && str.regionMatches(true, 0, prefix, 0, prefixLength);
  }

  @Contract(pure = true)
  public static boolean endsWithIgnoreCase(@NotNull CharSequence text, @NotNull CharSequence suffix) {
    int l1 = text.length();
    int l2 = suffix.length();
    if (l1 < l2) return false;

    for (int i = l1 - 1; i >= l1 - l2; i--) {
      if (!charsEqualIgnoreCase(text.charAt(i), suffix.charAt(i + l2 - l1))) {
        return false;
      }
    }

    return true;
  }

  /**
   * Allows to retrieve index of last occurrence of the given symbols at {@code [start; end)} sub-sequence of the given text.
   *
   * @param s     target text
   * @param c     target symbol which last occurrence we want to check
   * @param start start offset of the target text (inclusive)
   * @param end   end offset of the target text (exclusive)
   * @return index of the last occurrence of the given symbol at the target sub-sequence of the given text if any;
   * {@code -1} otherwise
   */
  @Contract(pure = true)
  public static int lastIndexOf(@NotNull CharSequence s, char c, int start, int end) {
    start = Math.max(start, 0);
    for (int i = Math.min(end, s.length()) - 1; i >= start; i--) {
      if (s.charAt(i) == c) return i;
    }
    return -1;
  }

  @Contract(value = "null -> true",pure = true)
  public static boolean isEmpty(@Nullable CharSequence cs) {
    return cs == null || cs.length() == 0;
  }

  @Contract(value = "null -> true", pure = true)
  public static boolean isEmptyOrSpaces(@Nullable CharSequence s) {
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

  @NotNull
  @Contract(pure = true)
  public static String notNullize(@Nullable String s) {
    return notNullize(s, "");
  }

  @NotNull
  @Contract(pure = true)
  public static String notNullize(@Nullable String s, @NotNull String defaultValue) {
    return s == null ? defaultValue : s;
  }

  @NotNull
  @Contract(pure = true)
  public static List<String> splitHonorQuotes(@NotNull String s, char separator) {
    List<String> result = new ArrayList<String>();
    StringBuilder builder = new StringBuilder(s.length());
    boolean inQuotes = false;
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      if (c == separator && !inQuotes) {
        if (builder.length() > 0) {
          result.add(builder.toString());
          builder.setLength(0);
        }
        continue;
      }
      if ((c == '"' || c == '\'') && !(i > 0 && s.charAt(i - 1) == '\\')) {
        inQuotes = !inQuotes;
      }
      builder.append(c);
    }
    if (builder.length() > 0) {
      result.add(builder.toString());
    }
    return result;
  }

  @NotNull
  @Contract(pure = true)
  public static String formatFileSize(long fileSize) {
    return formatFileSize(fileSize, " ");
  }

  @NotNull
  @Contract(pure = true)
  public static String formatFileSize(long fileSize, @NotNull String unitSeparator) {
    if (fileSize < 0) throw new IllegalArgumentException("Invalid value: " + fileSize);
    if (fileSize == 0) return '0' + unitSeparator + 'B';
    int rank = (int)((Math.log10(fileSize) + 0.0000021714778384307465) / 3);  // (3 - Math.log10(999.995))
    double value = fileSize / Math.pow(1000, rank);
    String[] units = {"B", "kB", "MB", "GB", "TB", "PB", "EB"};
    return new DecimalFormat("0.##").format(value) + unitSeparator + units[rank];
  }

  /**
   * @return true if the string starts and ends with quote (") or apostrophe (')
   */
  @Contract(pure = true)
  public static boolean isQuotedString(@NotNull String s) {
    return s.length() > 1 && (s.charAt(0) == '\'' || s.charAt(0) == '\"') && s.charAt(0) == s.charAt(s.length() - 1);
  }

  @NotNull
  @Contract(pure = true)
  public static String unquoteString(@NotNull String s) {
    return isQuotedString(s) ? s.substring(1, s.length() - 1) : s;
  }

  @NotNull
  @Contract(pure = true)
  public static String unquoteString(@NotNull String s, char quotationChar) {
    boolean quoted = s.length() > 1 && quotationChar == s.charAt(0) && quotationChar == s.charAt(s.length() - 1);
    return quoted ? s.substring(1, s.length() - 1) : s;
  }

  @Contract(pure = true)
  public static boolean startsWith(@NotNull CharSequence text, @NotNull CharSequence prefix) {
    int l1 = text.length();
    int l2 = prefix.length();
    if (l1 < l2) return false;

    for (int i = 0; i < l2; i++) {
      if (text.charAt(i) != prefix.charAt(i)) return false;
    }

    return true;
  }
}