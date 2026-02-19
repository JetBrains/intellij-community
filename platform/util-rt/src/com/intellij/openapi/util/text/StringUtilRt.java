// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
public final class StringUtilRt {

  private StringUtilRt() { }

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
  public static char toUpperCase(char ch) {
    //if (a < 'a') return a;
    //if (a <= 'z') return (char)(ch + ('A' - 'a'));

    if (ch <= 0x7F) {
      if (ch >= 'a' && ch <= 'z') {
        //in ASCII lower and upper case letters differ by a single bit:
        return (char)(ch & 0b1101_1111);
        //legacy version: (char)(ch + ('A' - 'a')) -- a bit slower in benchmarks
      }
      return ch;
    }
    return Character.toUpperCase(ch);
  }

  @Contract(pure = true)
  public static char toLowerCase(char ch) {
    if (ch <= 0x7F) {
      if (ch >= 'A' && ch <= 'Z') {
        //in ASCII lower and upper case letters differ by a single bit:
        return (char)(ch | 0b0010_0000);
        //legacy version: (char)(ch + ('a' - 'A')) -- a bit slower in benchmarks
      }
      return ch;
    }
    return Character.toLowerCase(ch);
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
  public static String convertLineSeparators(@NotNull String text, @NotNull String newSeparator, int @Nullable [] offsetsToKeep) {
    return convertLineSeparators(text, newSeparator, offsetsToKeep, false);
  }

  @NotNull
  public static String convertLineSeparators(@NotNull String text,
                                             @NotNull String newSeparator,
                                             int @Nullable [] offsetsToKeep,
                                             boolean keepCarriageReturn) {
    return unifyLineSeparators(text, newSeparator, offsetsToKeep, keepCarriageReturn).toString();
  }

  @NotNull
  private static CharSequence unifyLineSeparators(@NotNull CharSequence text,
                                                  @NotNull String newSeparator,
                                                  int @Nullable [] offsetsToKeep,
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
      else if (buffer == null) {
        intactLength++;
      }
      else {
        buffer.append(c);
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
      try {
        return Integer.parseInt(string);
      }
      catch (NumberFormatException ignored) {
      }
    }
    return defaultValue;
  }

  @Contract(pure = true)
  public static long parseLong(@Nullable String string, long defaultValue) {
    if (string != null) {
      try {
        return Long.parseLong(string);
      }
      catch (NumberFormatException ignored) {
      }
    }
    return defaultValue;
  }

  @Contract(pure = true)
  public static double parseDouble(@Nullable String string, double defaultValue) {
    if (string != null) {
      try {
        return Double.parseDouble(string);
      }
      catch (NumberFormatException ignored) {
      }
    }
    return defaultValue;
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
    return startsWithIgnoreCase(str, 0, prefix);
  }

  @Contract(pure = true)
  public static boolean startsWithIgnoreCase(@NotNull String str, int startOffset, @NotNull String prefix) {
    int stringLength = str.length();
    int prefixLength = prefix.length();
    return stringLength >= prefixLength && str.regionMatches(true, startOffset, prefix, 0, prefixLength);
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

  @Contract(value = "null -> true", pure = true)
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
    List<String> result = new ArrayList<>();
    StringBuilder builder = new StringBuilder(s.length());
    char quote = 0;
    boolean isEscaped = false;
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      boolean isSeparator = c == separator;
      boolean isQuote = c == '"' || c == '\'';
      boolean isQuoted = quote != 0;
      boolean isEscape = c == '\\';

      if (!isQuoted && isSeparator) {
        if (builder.length() > 0) {
          result.add(builder.toString());
          builder.setLength(0);
        }
        continue;
      }

      if (!isEscaped && isQuote && (quote == 0 || quote == c)) {
        quote = isQuoted ? 0 : c;
      }

      isEscaped = isEscape && !isEscaped;

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
    return formatFileSize(fileSize, " ", -1);
  }

  @NotNull
  @Contract(pure = true)
  public static String formatFileSize(long fileSize, @NotNull String unitSeparator) {
    return formatFileSize(fileSize, unitSeparator, -1);
  }

  @NotNull
  @Contract(pure = true)
  public static String formatFileSize(long fileSize, @NotNull String unitSeparator, int rank) {
    return formatFileSize(fileSize, unitSeparator, rank, false);
  }

  /**
   * @param fileSize               - size of the file in bytes
   * @param unitSeparator          - separator inserted between value and unit
   * @param rank                   - preferred rank. 0 - bytes, 1 - kilobytes, ..., 6 - exabytes. If less than 0 then picked automatically
   * @param fixedFractionPrecision - keep the fraction precision. if true, a number like 5.50 will be kept as it is, otherwise it will be
   *                               rounded to 5.5
   * @return string with formatted file size
   */
  @NotNull
  @Contract(pure = true)
  public static String formatFileSize(long fileSize, @NotNull String unitSeparator, int rank, boolean fixedFractionPrecision) {
    if (fileSize < 0) throw new IllegalArgumentException("Invalid value: " + fileSize);
    if (fileSize == 0) return '0' + unitSeparator + 'B';
    if (rank < 0) {
      rank = rankForFileSize(fileSize);
    }
    double value = fileSize / Math.pow(1000, rank);
    String[] units = {"B", "kB", "MB", "GB", "TB", "PB", "EB"};
    DecimalFormat decimalFormat = new DecimalFormat("0.##");
    if (fixedFractionPrecision) {
      decimalFormat.setMinimumFractionDigits(2);
    }
    return decimalFormat.format(value) + unitSeparator + units[rank];
  }

  @Contract(pure = true)
  public static int rankForFileSize(long fileSize) {
    if (fileSize < 0) throw new IllegalArgumentException("Invalid value: " + fileSize);
    return (int)((Math.log10(fileSize) + 0.0000021714778384307465) / 3);  // (3 - Math.log10(999.995))
  }

  /**
   * @return true if the string starts and ends with quote (") or apostrophe (')
   */
  @Contract(pure = true)
  public static boolean isQuotedString(@NotNull String s) {
    int length = s.length();
    if (length <= 1) return false;
    char firstChar = s.charAt(0);
    if (firstChar != '\'' && firstChar != '\"') return false;
    return firstChar == s.charAt(length - 1);
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

  @Contract(pure = true)
  public static int stringHashCodeInsensitive(@NotNull CharSequence chars) {
    return stringHashCodeInsensitive(chars, 0, chars.length());
  }

  @Contract(pure = true)
  public static int stringHashCodeInsensitive(@NotNull CharSequence chars, int from, int to) {
    return stringHashCodeInsensitive(chars, from, to, 0);
  }

  @Contract(pure = true)
  public static int stringHashCodeInsensitive(@NotNull CharSequence chars, int from, int to, int prefixHash) {
    int h = prefixHash;
    for (int off = from; off < to; off++) {
      h = 31 * h + toLowerCase(chars.charAt(off));
    }
    return h;
  }
}