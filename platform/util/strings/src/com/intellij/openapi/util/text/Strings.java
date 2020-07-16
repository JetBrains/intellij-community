// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.util.text;

import com.intellij.util.ArrayUtilRt;
import com.intellij.util.text.CharArrayCharSequence;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.Objects;

public final class Strings {
  public static final CharSequence EMPTY_CHAR_SEQUENCE = new CharArrayCharSequence(ArrayUtilRt.EMPTY_CHAR_ARRAY);

  public static boolean isAscii(char ch) {
    return ch < 128;
  }

  @Contract(pure = true)
  public static int compare(char c1, char c2, boolean ignoreCase) {
    // duplicating String.equalsIgnoreCase logic
    int d = c1 - c2;
    if (d == 0 || !ignoreCase) {
      return d;
    }
    // If characters don't match but case may be ignored,
    // try converting both characters to uppercase.
    // If the results match, then the comparison scan should
    // continue.
    char u1 = StringUtilRt.toUpperCase(c1);
    char u2 = StringUtilRt.toUpperCase(c2);
    d = u1 - u2;
    if (d != 0) {
      // Unfortunately, conversion to uppercase does not work properly
      // for the Georgian alphabet, which has strange rules about case
      // conversion.  So we need to make one last check before
      // exiting.
      d = StringUtilRt.toLowerCase(u1) - StringUtilRt.toLowerCase(u2);
    }
    return d;
  }

  @Contract(pure = true)
  public static boolean charsMatch(char c1, char c2, boolean ignoreCase) {
    return compare(c1, c2, ignoreCase) == 0;
  }

  @Contract(value = "null -> null; !null -> !null", pure = true)
  public static String toLowerCase(@Nullable String str) {
    return str == null ? null : str.toLowerCase(Locale.ENGLISH);
  }

  @Contract(pure = true)
  public static char toLowerCase(final char a) {
    return StringUtilRt.toLowerCase(a);
  }

  @Contract(value = "null -> null; !null -> !null", pure = true)
  public static String toUpperCase(String s) {
    return s == null ? null : s.toUpperCase(Locale.ENGLISH);
  }

  @Contract(pure = true)
  public static char toUpperCase(char a) {
    return StringUtilRt.toUpperCase(a);
  }

  @Contract(pure = true)
  public static boolean contains(@NotNull CharSequence sequence, @NotNull CharSequence infix) {
    return indexOf(sequence, infix) >= 0;
  }

  /**
   * Allows to answer if target symbol is contained at given char sequence at {@code [start; end)} interval.
   *
   * @param s     target char sequence to check
   * @param start start offset to use within the given char sequence (inclusive)
   * @param end   end offset to use within the given char sequence (exclusive)
   * @param c     target symbol to check
   * @return {@code true} if given symbol is contained at the target range of the given char sequence;
   * {@code false} otherwise
   */
  @Contract(pure = true)
  public static boolean contains(@NotNull CharSequence s, int start, int end, char c) {
    return indexOf(s, c, start, end) >= 0;
  }


  @Contract(pure = true)
  public static boolean containsChar(final @NotNull String value, final char ch) {
    return value.indexOf(ch) >= 0;
  }

  @Contract(pure = true)
  public static boolean containsAnyChar(final @NotNull String value, final @NotNull String chars) {
    return chars.length() > value.length()
           ? containsAnyChar(value, chars, 0, value.length())
           : containsAnyChar(chars, value, 0, chars.length());
  }

  @Contract(pure = true)
  public static boolean containsAnyChar(final @NotNull String value,
                                        final @NotNull String chars,
                                        final int start, final int end) {
    for (int i = start; i < end; i++) {
      if (chars.indexOf(value.charAt(i)) >= 0) {
        return true;
      }
    }

    return false;
  }

  @Contract(pure = true)
  public static int indexOf(@NotNull CharSequence s, char c) {
    return indexOf(s, c, 0, s.length());
  }

  @Contract(pure = true)
  public static int indexOf(@NotNull CharSequence s, char c, int start) {
    return indexOf(s, c, start, s.length());
  }

  @Contract(pure = true)
  public static int indexOf(@NotNull CharSequence s, char c, int start, int end) {
    end = Math.min(end, s.length());
    for (int i = Math.max(start, 0); i < end; i++) {
      if (s.charAt(i) == c) return i;
    }
    return -1;
  }

  @Contract(pure = true)
  public static int indexOf(@NotNull CharSequence sequence, @NotNull CharSequence infix) {
    return indexOf(sequence, infix, 0);
  }

  @Contract(pure = true)
  public static int indexOf(@NotNull CharSequence sequence, @NotNull CharSequence infix, int start) {
    return indexOf(sequence, infix, start, sequence.length());
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
  public static int indexOf(@NotNull CharSequence s, char c, int start, int end, boolean caseSensitive) {
    end = Math.min(end, s.length());
    for (int i = Math.max(start, 0); i < end; i++) {
      if (charsMatch(s.charAt(i), c, !caseSensitive)) return i;
    }
    return -1;
  }

  @Contract(pure = true)
  public static int indexOf(char @NotNull [] s, char c, int start, int end, boolean caseSensitive) {
    end = Math.min(end, s.length);
    for (int i = Math.max(start, 0); i < end; i++) {
      boolean ignoreCase = !caseSensitive;
      if (charsMatch(s[i], c, ignoreCase)) return i;
    }
    return -1;
  }

  @Contract(pure = true)
  public static int indexOfAny(final @NotNull String s, final @NotNull String chars) {
    return indexOfAny(s, chars, 0, s.length());
  }

  @Contract(pure = true)
  public static int indexOfAny(final @NotNull CharSequence s, final @NotNull String chars) {
    return indexOfAny(s, chars, 0, s.length());
  }

  @Contract(pure = true)
  public static int indexOfAny(final @NotNull String s, final @NotNull String chars, final int start, final int end) {
    return indexOfAny((CharSequence)s, chars, start, end);
  }

  @Contract(pure = true)
  public static int indexOfAny(final @NotNull CharSequence s, final @NotNull String chars, final int start, int end) {
    if (chars.length() == 0) return -1;

    end = Math.min(end, s.length());
    for (int i = Math.max(start, 0); i < end; i++) {
      if (containsChar(chars, s.charAt(i))) return i;
    }
    return -1;
  }

  @Contract(pure = true)
  public static int indexOfIgnoreCase(@NotNull String where, @NotNull String what, int fromIndex) {
    return indexOfIgnoreCase((CharSequence)where, what, fromIndex);
  }

  /**
   * Implementation copied from {@link String#indexOf(String, int)} except character comparisons made case insensitive
   */
  @Contract(pure = true)
  public static int indexOfIgnoreCase(@NotNull CharSequence where, @NotNull CharSequence what, int fromIndex) {
    int targetCount = what.length();
    int sourceCount = where.length();

    if (fromIndex >= sourceCount) {
      return targetCount == 0 ? sourceCount : -1;
    }

    if (fromIndex < 0) {
      fromIndex = 0;
    }

    if (targetCount == 0) {
      return fromIndex;
    }

    char first = what.charAt(0);
    int max = sourceCount - targetCount;

    for (int i = fromIndex; i <= max; i++) {
      /* Look for first character. */
      if (!charsEqualIgnoreCase(where.charAt(i), first)) {
        //noinspection StatementWithEmptyBody,AssignmentToForLoopParameter
        while (++i <= max && !charsEqualIgnoreCase(where.charAt(i), first)) ;
      }

      /* Found first character, now look at the rest of v2 */
      if (i <= max) {
        int j = i + 1;
        int end = j + targetCount - 1;
        //noinspection StatementWithEmptyBody
        for (int k = 1; j < end && charsEqualIgnoreCase(where.charAt(j), what.charAt(k)); j++, k++) ;

        if (j == end) {
          /* Found whole string. */
          return i;
        }
      }
    }

    return -1;
  }

  @Contract(pure = true)
  public static int indexOfIgnoreCase(@NotNull String where, char what, int fromIndex) {
    int sourceCount = where.length();
    for (int i = Math.max(fromIndex, 0); i < sourceCount; i++) {
      if (charsEqualIgnoreCase(where.charAt(i), what)) {
        return i;
      }
    }

    return -1;
  }

  @Contract(pure = true)
  public static boolean charsEqualIgnoreCase(char a, char b) {
    return charsMatch(a, b, true);
  }

  /**
   * Capitalize the first letter of the sentence.
   */
  @Contract(pure = true)
  public static @NotNull String capitalize(@NotNull String s) {
    if (s.isEmpty()) return s;
    if (s.length() == 1) return toUpperCase(s);
    if (Character.isUpperCase(s.charAt(0))) return s;
    return toUpperCase(s.charAt(0)) + s.substring(1);
  }

  @Contract(value = "null -> false", pure = true)
  public static boolean isCapitalized(@Nullable String s) {
    return s != null && !s.isEmpty() && Character.isUpperCase(s.charAt(0));
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

  @Contract(pure = true)
  public static boolean endsWith(@NotNull CharSequence text, @NotNull CharSequence suffix) {
    return StringUtilRt.endsWith(text, suffix);
  }

  @Contract(pure = true)
  public static boolean endsWithChar(@Nullable CharSequence s, char suffix) {
    return StringUtilRt.endsWithChar(s, suffix);
  }

  @Contract(pure = true)
  public static boolean endsWithIgnoreCase(@NotNull String str, @NotNull String suffix) {
    return StringUtilRt.endsWithIgnoreCase(str, suffix);
  }

  @Contract(value = "null -> false", pure = true)
  public static boolean isNotEmpty(@Nullable String s) {
    return !isEmpty(s);
  }

  @Contract(value = "null -> true", pure = true)
  public static boolean isEmpty(@Nullable String s) {
    return s == null || s.isEmpty();
  }

  @Contract(value = "null -> true", pure = true)
  public static boolean isEmpty(@Nullable CharSequence cs) {
    return StringUtilRt.isEmpty(cs);
  }

  @Contract(pure = true)
  public static @NotNull String pluralize(@NotNull String word) {
    String plural = Pluralizer.PLURALIZER.plural(word);
    if (plural != null) return plural;
    if (word.endsWith("s")) return Pluralizer.restoreCase(word, word + "es");
    return Pluralizer.restoreCase(word, word + "s");
  }

  /**
   * Returns unpluralized variant using English based heuristics like properties -> property, names -> name, children -> child.
   * Returns {@code null} if failed to match appropriate heuristic.
   *
   * @param word english word in plural form
   * @return name in singular form or {@code null} if failed to find one.
   */
  @Contract(pure = true)
  public static @Nullable String unpluralize(@NotNull String word) {
    String singular = Pluralizer.PLURALIZER.singular(word);
    if (singular != null) return singular;
    if (word.endsWith("es")) return nullize(trimEnd(word, "es", true));
    if (word.endsWith("s")) return nullize(trimEnd(word, "s", true));
    return null;
  }

  @Contract(pure = true)
  public static @NotNull String notNullize(@Nullable String s) {
    return StringUtilRt.notNullize(s);
  }

  @Contract(pure = true)
  public static @NotNull String notNullize(@Nullable String s, @NotNull String defaultValue) {
    return StringUtilRt.notNullize(s, defaultValue);
  }

  @Contract(pure = true)
  public static @Nullable String nullize(@Nullable String s) {
    return nullize(s, false);
  }

  @Contract(pure = true)
  public static @Nullable String nullize(@Nullable String s, @Nullable String defaultValue) {
    boolean empty = isEmpty(s) || Objects.equals(s, defaultValue);
    return empty ? null : s;
  }

  @Contract(pure = true)
  public static @Nullable String nullize(@Nullable String s, boolean nullizeSpaces) {
    boolean empty = nullizeSpaces ? isEmptyOrSpaces(s) : isEmpty(s);
    return empty ? null : s;
  }

  @Contract(value = "null -> true", pure = true)
  public static boolean isEmptyOrSpaces(@Nullable CharSequence s) {
    return StringUtilRt.isEmptyOrSpaces(s);
  }

  @Contract(value = "null -> null; !null -> !null", pure = true)
  public static String trim(@Nullable String s) {
    return s == null ? null : s.trim();
  }

  @Contract(pure = true)
  public static @NotNull String trimEnd(@NotNull String s, @NotNull String suffix) {
    return trimEnd(s, suffix, false);
  }

  @Contract(pure = true)
  public static @NotNull String trimEnd(@NotNull String s, @NotNull String suffix, boolean ignoreCase) {
    boolean endsWith = ignoreCase ? endsWithIgnoreCase(s, suffix) : s.endsWith(suffix);
    if (endsWith) {
      return s.substring(0, s.length() - suffix.length());
    }
    return s;
  }

  @Contract(pure = true)
  public static @NotNull String trimEnd(@NotNull String s, char suffix) {
    if (endsWithChar(s, suffix)) {
      return s.substring(0, s.length() - 1);
    }
    return s;
  }

  @Contract(pure = true)
  public static int stringHashCode(@NotNull CharSequence chars, int from, int to) {
    return stringHashCode(chars, from, to, 0);
  }

  @Contract(pure = true)
  public static int stringHashCode(@NotNull CharSequence chars, int from, int to, int prefixHash) {
    int h = prefixHash;
    for (int off = from; off < to; off++) {
      h = 31 * h + chars.charAt(off);
    }
    return h;
  }

  @Contract(pure = true)
  public static int stringHashCode(char[] chars, int from, int to) {
    int h = 0;
    for (int off = from; off < to; off++) {
      h = 31 * h + chars[off];
    }
    return h;
  }

  @Contract(pure = true)
  public static int stringHashCodeInsensitive(char @NotNull [] chars, int from, int to) {
    int h = 0;
    for (int off = from; off < to; off++) {
      h = 31 * h + toLowerCase(chars[off]);
    }
    return h;
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

  @Contract(pure = true)
  public static int stringHashCodeInsensitive(@NotNull CharSequence chars) {
    return stringHashCodeInsensitive(chars, 0, chars.length());
  }
}