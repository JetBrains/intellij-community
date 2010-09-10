/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.util.text;

import com.google.common.collect.Lists;
import com.intellij.CommonBundle;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.util.*;
import com.intellij.util.text.CharArrayCharSequence;
import com.intellij.util.text.LineReader;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.beans.Introspector;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.text.DateFormat;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

//TeamCity inherits StringUtil: do not add private constructors!!!
@SuppressWarnings({"UtilityClassWithoutPrivateConstructor"})
public class StringUtil {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.util.text.StringUtil");
  @NonNls private static final String VOWELS = "aeiouy";
  private static final Pattern EOL_SPLIT_PATTERN = Pattern.compile(" *(\r|\n|\r\n)+ *");
  public static final NotNullFunction<String, String> QUOTER = new NotNullFunction<String, String>() {
    @NotNull
    public String fun(String s) {
      return "\"" + s + "\"";
    }
  };

  public static <T> Function<T, String> createToStringFunction(Class<T> cls) {
    return new Function<T, String>() {
      @Override
      public String fun(T o) {
        return o.toString();
      }
    };
  }

  public static Function<String, String> TRIMMER = new Function<String, String>() {
    @SuppressWarnings({"ConstantConditions"})
    @Override
    public String fun(String s) {
      return s == null ? null : s.trim();
    }
  };

  public static String replace(@NonNls @NotNull String text, @NonNls @NotNull String oldS, @NonNls @Nullable String newS) {
    return replace(text, oldS, newS, false);
  }

  public static String replaceIgnoreCase(@NotNull String text, @NotNull String oldS, @Nullable String newS) {
    return replace(text, oldS, newS, true);
  }

  public static void replaceChar(@NotNull char[] buffer, char oldChar, char newChar, int start, int end) {
    for (int i = start; i < end; i++) {
      char c = buffer[i];
      if (c == oldChar) {
        buffer[i] = newChar;
      }
    }
  }

  public static String replace(@NotNull final String text, @NotNull final String oldS, @Nullable final String newS, boolean ignoreCase) {
    if (text.length() < oldS.length()) return text;

    final String text1 = ignoreCase ? text.toLowerCase() : text;
    final String oldS1 = ignoreCase ? oldS.toLowerCase() : oldS;
    final StringBuilder newText = new StringBuilder();
    int i = 0;
    while (i < text1.length()) {
      int i1 = text1.indexOf(oldS1, i);
      if (i1 < 0) {
        if (i == 0) return text;
        newText.append(text, i, text.length());
        break;
      }
      else {
        if (newS == null) return null;
        newText.append(text, i, i1);
        newText.append(newS);
        i = i1 + oldS.length();
      }
    }
    return newText.toString();
  }

  @NotNull
  public static String getShortName(@NotNull String fqName) {
    return getShortName(fqName, '.');
  }

  @NotNull
  public static String getShortName(@NotNull Class aClass) {
    return getShortName(aClass.getName());
  }

  /**
   * Implementation copied from {@link String#indexOf(String, int)} except character comparisons made case insensitive
   */
  public static int indexOfIgnoreCase(@NotNull String where, @NotNull String what, int fromIndex) {
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
        while (++i <= max && !charsEqualIgnoreCase(where.charAt(i), first)) ;
      }

      /* Found first character, now look at the rest of v2 */
      if (i <= max) {
        int j = i + 1;
        int end = j + targetCount - 1;
        for (int k = 1; j < end && charsEqualIgnoreCase(where.charAt(j), what.charAt(k)); j++, k++) ;

        if (j == end) {
          /* Found whole string. */
          return i;
        }
      }
    }

    return -1;
  }

  public static boolean containsIgnoreCase(@NotNull String where, @NotNull String what) {
    return indexOfIgnoreCase(where, what, 0) >= 0;
  }

  public static boolean endsWithIgnoreCase(@NonNls @NotNull String str, @NonNls @NotNull String suffix) {
    final int stringLength = str.length();
    final int suffixLength = suffix.length();
    return stringLength >= suffixLength && str.regionMatches(true, stringLength - suffixLength, suffix, 0, suffixLength);
  }

  public static boolean startsWithIgnoreCase(@NonNls @NotNull String str, @NonNls @NotNull String prefix) {
    final int stringLength = str.length();
    final int prefixLength = prefix.length();
    return stringLength >= prefixLength && str.regionMatches(true, 0, prefix, 0, prefixLength);
  }

  public static boolean charsEqualIgnoreCase(char a, char b) {
    return a == b || toUpperCase(a) == toUpperCase(b) || toLowerCase(a) == toLowerCase(b);
  }

  public static char toUpperCase(char a) {
    if (a < 'a') {
      return a;
    }
    if (a >= 'a' && a <= 'z') {
      return (char)(a + ('A' - 'a'));
    }
    return Character.toUpperCase(a);
  }

  public static char toLowerCase(final char a) {
    if (a < 'A' || a >= 'a' && a <= 'z') {
      return a;
    }

    if (a >= 'A' && a <= 'Z') {
      return (char)(a + ('a' - 'A'));
    }

    return Character.toLowerCase(a);
  }

  @Nullable
  public static String toLowerCase(@Nullable final String str) {
    return str == null ? null : str.toLowerCase();
  }

  @NotNull
  public static String getShortName(@NotNull String fqName, char separator) {
    int lastPointIdx = fqName.lastIndexOf(separator);
    if (lastPointIdx >= 0) {
      return fqName.substring(lastPointIdx + 1);
    }
    return fqName;
  }

  @NotNull
  public static String getPackageName(@NotNull String fqName) {
    return getPackageName(fqName, '.');
  }

  @NotNull
  public static String getPackageName(@NotNull String fqName, char separator) {
    int lastPointIdx = fqName.lastIndexOf(separator);
    if (lastPointIdx >= 0) {
      return fqName.substring(0, lastPointIdx);
    }
    return "";
  }

  /**
   * Converts line separators to <code>"\n"</code>
   */
  @NotNull
  public static String convertLineSeparators(@NotNull String text) {
    return convertLineSeparators(text, "\n", null);
  }

  @NotNull
  public static String convertLineSeparators(@NotNull String text, @NotNull String newSeparator) {
    return convertLineSeparators(text, newSeparator, null);
  }

  @NotNull
  public static String convertLineSeparators(@NotNull String text, @NotNull String newSeparator, @Nullable int[] offsetsToKeep) {
    StringBuilder buffer = null;
    int intactLength = 0;
    final boolean newSeparatorIsSlashN = "\n".equals(newSeparator);
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
          buffer.append(c);
        }
      }
      else if (c == '\r') {
        if (buffer == null) {
          buffer = new StringBuilder(text.length());
          buffer.append(text, 0, intactLength);
        }
        buffer.append(newSeparator);
        if (i < text.length() - 1 && text.charAt(i + 1) == '\n') {
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
    return buffer == null ? text : buffer.toString();
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

  public static int getLineBreakCount(@NotNull CharSequence text) {
    int count = 0;
    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);
      if (c == '\n') {
        count++;
      }
      else if (c == '\r') {
        if (i + 1 < text.length() && text.charAt(i + 1) == '\n') {
          //noinspection AssignmentToForLoopParameter
          i++;
          count++;
        }
        else {
          count++;
        }
      }
    }
    return count;
  }

  public static boolean containsLineBreak(@NotNull CharSequence text) {
    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);
      if (c == '\n' || c == '\r') return true;
    }
    return false;
  }

  public static int lineColToOffset(@NotNull CharSequence text, int line, int col) {
    int curLine = 0;
    int offset = 0;
    while (line != curLine) {
      if (offset == text.length()) return -1;
      char c = text.charAt(offset);
      if (c == '\n') {
        curLine++;
      }
      else if (c == '\r') {
        curLine++;
        if (offset < text.length() - 1 && text.charAt(offset + 1) == '\n') {
          offset++;
        }
      }
      offset++;
    }
    return offset + col;
  }

  public static int offsetToLineNumber(@NotNull CharSequence text, int offset) {
    int curLine = 0;
    int curOffset = 0;
    while (curOffset < offset) {
      if (curOffset == text.length()) return -1;
      char c = text.charAt(curOffset);
      if (c == '\n') {
        curLine++;
      }
      else if (c == '\r') {
        curLine++;
        if (curOffset < text.length() - 1 && text.charAt(curOffset + 1) == '\n') {
          curOffset++;
        }
      }
      curOffset++;
    }
    return curLine;
  }

  /**
   * Classic dynamic programming algorithm for string differences.
   */
  public static int difference(@NotNull String s1, @NotNull String s2) {
    int[][] a = new int[s1.length()][s2.length()];

    for (int i = 0; i < s1.length(); i++) {
      a[i][0] = i;
    }

    for (int j = 0; j < s2.length(); j++) {
      a[0][j] = j;
    }

    for (int i = 1; i < s1.length(); i++) {
      for (int j = 1; j < s2.length(); j++) {

        a[i][j] = Math.min(Math.min(a[i - 1][j - 1] + (s1.charAt(i) == s2.charAt(j) ? 0 : 1), a[i - 1][j] + 1), a[i][j - 1] + 1);
      }
    }

    return a[s1.length() - 1][s2.length() - 1];
  }

  @NotNull
  public static String wordsToBeginFromUpperCase(@NotNull String s) {
    return toTitleCase(s, ourPrepositions);
  }

  @NotNull
  public static String toTitleCase(@NotNull String s) {
    return toTitleCase(s, ArrayUtil.EMPTY_STRING_ARRAY);
  }

  @NotNull
  private static String toTitleCase(@NotNull String s, @NotNull String[] prepositions) {
    StringBuffer buffer = null;
    for (int i = 0; i < s.length(); i++) {
      char prevChar = i == 0 ? ' ' : s.charAt(i - 1);
      char currChar = s.charAt(i);
      if (!Character.isLetterOrDigit(prevChar)) {
        if (Character.isLetterOrDigit(currChar)) {
          if (!Character.isUpperCase(currChar)) {
            int j = i;
            for (; j < s.length(); j++) {
              if (!Character.isLetterOrDigit(s.charAt(j))) {
                break;
              }
            }
            if (!isPreposition(s, i, j - 1, prepositions)) {
              if (buffer == null) {
                buffer = new StringBuffer(s);
              }
              buffer.setCharAt(i, toUpperCase(currChar));
            }
          }
        }
      }
    }
    if (buffer == null) {
      return s;
    }
    else {
      return buffer.toString();
    }
  }

  @NonNls private static final String[] ourPrepositions = {"at", "the", "and", "not", "if", "a", "or", "to", "in", "on", "into"};


  public static boolean isPreposition(@NotNull String s, int firstChar, int lastChar) {
    return isPreposition(s, firstChar, lastChar, ourPrepositions);
  }

  public static boolean isPreposition(@NotNull String s, int firstChar, int lastChar, @NotNull String[] prepositions) {
    for (String preposition : prepositions) {
      boolean found = false;
      if (lastChar - firstChar + 1 == preposition.length()) {
        found = true;
        for (int j = 0; j < preposition.length(); j++) {
          if (!(toLowerCase(s.charAt(firstChar + j)) == preposition.charAt(j))) {
            found = false;
          }
        }
      }
      if (found) {
        return true;
      }
    }
    return false;
  }

  public static void escapeStringCharacters(int length, @NotNull String str, @NotNull @NonNls StringBuilder buffer) {
    escapeStringCharacters(length, str, "\"", buffer);
  }

  @NotNull
  public static StringBuilder escapeStringCharacters(int length,
                                                     @NotNull String str,
                                                     @Nullable String additionalChars,
                                                     @NotNull @NonNls StringBuilder buffer) {
    for (int idx = 0; idx < length; idx++) {
      char ch = str.charAt(idx);
      switch (ch) {
        case '\b':
          buffer.append("\\b");
          break;

        case '\t':
          buffer.append("\\t");
          break;

        case '\n':
          buffer.append("\\n");
          break;

        case '\f':
          buffer.append("\\f");
          break;

        case '\r':
          buffer.append("\\r");
          break;

        case '\\':
          buffer.append("\\\\");
          break;

        default:
          if (additionalChars != null && additionalChars.indexOf(ch) > -1) {
            buffer.append("\\").append(ch);
          }
          else if (Character.isISOControl(ch)) {
            String hexCode = Integer.toHexString(ch).toUpperCase();
            buffer.append("\\u");
            int paddingCount = 4 - hexCode.length();
            while (paddingCount-- > 0) {
              buffer.append(0);
            }
            buffer.append(hexCode);
          }
          else {
            buffer.append(ch);
          }
      }
    }
    return buffer;
  }

  @NotNull
  public static String escapeStringCharacters(@NotNull String s) {
    StringBuilder buffer = new StringBuilder();
    escapeStringCharacters(s.length(), s, buffer);
    return buffer.toString();
  }


  @NotNull
  public static String unescapeStringCharacters(@NotNull String s) {
    StringBuilder buffer = new StringBuilder();
    unescapeStringCharacters(s.length(), s, buffer);
    return buffer.toString();
  }

  @NotNull
  public static String unquoteString(@NotNull String s) {
    if (s.length() <= 1 || s.charAt(0) != '"' || s.charAt(s.length() - 1) != '"') {
      return s;
    }
    return s.substring(1, s.length() - 1);
  }

  /**
   * This is just an optimized version of Matcher.quoteReplacement
   */
  public static String quoteReplacement(String s) {
    boolean needReplacements = false;

    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      if (c == '\\' || c == '$') {
        needReplacements = true;
        break;
      }
    }

    if (!needReplacements) return s;

    StringBuilder sb = new StringBuilder(s.length() * 6 / 5);
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      if (c == '\\') {
        sb.append('\\');
        sb.append('\\');
      }
      else if (c == '$') {
        sb.append('\\');
        sb.append('$');
      }
      else {
        sb.append(c);
      }
    }
    return sb.toString();
  }

  private static void unescapeStringCharacters(int length, @NotNull String s, @NotNull StringBuilder buffer) {
    boolean escaped = false;
    for (int idx = 0; idx < length; idx++) {
      char ch = s.charAt(idx);
      if (!escaped) {
        if (ch == '\\') {
          escaped = true;
        }
        else {
          buffer.append(ch);
        }
      }
      else {
        switch (ch) {
          case 'n':
            buffer.append('\n');
            break;

          case 'r':
            buffer.append('\r');
            break;

          case 'b':
            buffer.append('\b');
            break;

          case 't':
            buffer.append('\t');
            break;

          case 'f':
            buffer.append('\f');
            break;

          case '\'':
            buffer.append('\'');
            break;

          case '\"':
            buffer.append('\"');
            break;

          case '\\':
            buffer.append('\\');
            break;

          case 'u':
            if (idx + 4 < length) {
              try {
                int code = Integer.valueOf(s.substring(idx + 1, idx + 5), 16).intValue();
                idx += 4;
                buffer.append((char)code);
              }
              catch (NumberFormatException e) {
                buffer.append("\\u");
              }
            }
            else {
              buffer.append("\\u");
            }
            break;

          default:
            buffer.append(ch);
            break;
        }
        escaped = false;
      }
    }

    if (escaped) buffer.append('\\');
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  @NotNull
  public static String pluralize(@NotNull String suggestion) {
    if (suggestion.endsWith("Child") || suggestion.endsWith("child")) {
      return suggestion + "ren";
    }

    if (endsWithChar(suggestion, 's') || endsWithChar(suggestion, 'x') || suggestion.endsWith("ch")) {
      return suggestion + "es";
    }

    int len = suggestion.length();
    if (endsWithChar(suggestion, 'y') && len > 1 && !isVowel(suggestion.charAt(len - 2))) {
      return suggestion.substring(0, len - 1) + "ies";
    }

    return suggestion + "s";
  }

  @NotNull
  public static String capitalizeWords(@NotNull String text,
                                       boolean allWords) {
    return capitalizeWords(text, " \t\n\r\f", allWords, false);
  }

  @NotNull
  public static String capitalizeWords(@NotNull String text,
                                       @NotNull String tokenizerDelim,
                                       boolean allWords,
                                       boolean leaveOriginalDelims) {
    final StringTokenizer tokenizer = new StringTokenizer(text, tokenizerDelim, leaveOriginalDelims);
    final StringBuilder out = new StringBuilder();
    boolean toCapitalize = true;
    while (tokenizer.hasMoreTokens()) {
      final String word = tokenizer.nextToken();
      if (!leaveOriginalDelims && out.length() > 0) {
        out.append(' ');
      }
      out.append(toCapitalize ? capitalize(word) : word);
      if (!allWords) {
        toCapitalize = false;
      }
    }
    return out.toString();
  }

  public static String decapitalize(String s) {
    return Introspector.decapitalize(s);
  }

  public static boolean isVowel(char c) {
    return VOWELS.indexOf(c) >= 0;
  }

  @NotNull
  public static String capitalize(@NotNull String s) {
    if (s.length() == 0) return s;
    if (s.length() == 1) return s.toUpperCase();

    // Optimization
    if (Character.isUpperCase(s.charAt(0))) return s;
    return toUpperCase(s.charAt(0)) + s.substring(1);
  }

  @NotNull
  public static String capitalizeWithJavaBeanConvention(@NotNull String s) {
    if (s.length() > 1 && Character.isUpperCase(s.charAt(1))) {
      return s;
    }
    return capitalize(s);
  }

  public static int stringHashCode(CharSequence chars) {
    if (chars instanceof String) return chars.hashCode();
    if (chars instanceof CharSequenceWithStringHash) return chars.hashCode();
    if (chars instanceof CharArrayCharSequence) return chars.hashCode();

    int h = 0;
    int to = chars.length();
    for (int off = 0; off < to; off++) {
      h = 31 * h + chars.charAt(off);
    }
    return h;
  }

  public static int stringHashCode(CharSequence chars, int from, int to) {
    int h = 0;
    for (int off = from; off < to; off++) {
      h = 31 * h + chars.charAt(off);
    }
    return h;
  }

  public static int stringHashCode(char[] chars, int from, int to) {
    int h = 0;
    for (int off = from; off < to; off++) {
      h = 31 * h + chars[off];
    }
    return h;
  }

  public static int stringHashCodeInsensitive(char[] chars, int from, int to) {
    int h = 0;
    for (int off = from; off < to; off++) {
      h = 31 * h + toLowerCase(chars[off]);
    }
    return h;
  }

  public static int stringHashCodeInsensitive(CharSequence chars, int from, int to) {
    int h = 0;
    for (int off = from; off < to; off++) {
      h = 31 * h + toLowerCase(chars.charAt(off));
    }
    return h;
  }

  public static int stringHashCodeInsensitive(@NotNull CharSequence chars) {
    int h = 0;
    final int len = chars.length();
    for (int i = 0; i < len; i++) {
      h = 31 * h + toLowerCase(chars.charAt(i));
    }
    return h;
  }

  /**
   * Equivalent to testee.startsWith(firstPrefix + secondPrefix) but avoids creating an object for concatenation.
   *
   * @param testee
   * @param firstPrefix
   * @param secondPrefix
   * @return
   */
  public static boolean startsWithConcatenationOf(String testee, String firstPrefix, String secondPrefix) {
    int l1 = firstPrefix.length();
    int l2 = secondPrefix.length();
    if (testee.length() < l1 + l2) return false;
    return testee.startsWith(firstPrefix) && testee.regionMatches(l1, secondPrefix, 0, l2);
  }

  @NotNull
  public static String trimEnd(@NotNull String s, @NonNls @NotNull String suffix) {
    if (s.endsWith(suffix)) {
      return s.substring(0, s.lastIndexOf(suffix));
    }
    return s;
  }

  public static boolean startsWithChar(@Nullable CharSequence s, char prefix) {
    return s != null && s.length() != 0 && s.charAt(0) == prefix;
  }

  public static boolean endsWithChar(@Nullable CharSequence s, char suffix) {
    return s != null && s.length() != 0 && s.charAt(s.length() - 1) == suffix;
  }

  @NotNull
  public static String trimStart(@NotNull String s, @NonNls @NotNull String prefix) {
    if (s.startsWith(prefix)) {
      return s.substring(prefix.length());
    }
    return s;
  }

  @NotNull
  public static String pluralize(@NotNull String base, int n) {
    if (n == 1) return base;
    return pluralize(base);
  }

  public static void repeatSymbol(@NotNull Appendable buffer, char symbol, int times) {
    assert times >= 0 : times;
    try {
      for (int i = 0; i < times; i++) {
        buffer.append(symbol);
      }
    }
    catch (IOException e) {
      LOG.error(e);
    }
  }

  public static boolean isNotEmpty(final String s) {
    return s != null && s.length() > 0;
  }

  public static boolean isEmpty(final String s) {
    return s == null || s.length() == 0;
  }

  @NotNull
  public static String notNullize(final String s) {
    return notNullize(s, "");
  }

  @NotNull
  public static String notNullize(final String s, @NotNull String defaultValue) {
    return s == null ? defaultValue : s;
  }

  @Nullable
  public static String nullize(final @Nullable String s) {
    if (isEmpty(s)) return null;
    return s;
  }

  public static boolean isEmptyOrSpaces(final String s) {
    return s == null || s.trim().length() == 0;
  }

  @NotNull
  public static String getThrowableText(@NotNull Throwable aThrowable) {
    return ExceptionUtil.getThrowableText(aThrowable);
  }

  @NotNull
  public static String getThrowableText(@NotNull Throwable aThrowable, @NonNls @NotNull final String stackFrameSkipPattern) {
    return ExceptionUtil.getThrowableText(aThrowable, stackFrameSkipPattern);
  }

  public static String getMessage(@NotNull Throwable e) {
    return ExceptionUtil.getMessage(e);
  }

  @NotNull
  public static String repeatSymbol(final char aChar, final int count) {
    char[] buffer = new char[count];
    Arrays.fill(buffer, aChar);
    return new String(buffer);
  }

  @NotNull
  public static List<String> splitHonorQuotes(@NotNull String s, char separator) {
    final ArrayList<String> result = new ArrayList<String>();
    final StringBuilder builder = new StringBuilder();
    boolean inQuotes = false;
    for (int i = 0; i < s.length(); i++) {
      final char c = s.charAt(i);
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
  public static List<String> split(@NotNull String s, @NotNull String separator) {
    return split(s, separator, true);
  }

  @NotNull
  public static List<String> split(@NotNull String s, @NotNull String separator,
                                   boolean excludeSeparator) {
    if (separator.length() == 0) {
      return Collections.singletonList(s);
    }
    List<String> result = new ArrayList<String>();
    int pos = 0;
    while (true) {
      int index = s.indexOf(separator, pos);
      if (index == -1) break;
      final int nextPos = index + separator.length();
      String token = s.substring(pos, excludeSeparator ? index : nextPos);
      if (token.length() != 0) {
        result.add(token);
      }
      pos = nextPos;
    }
    if (pos < s.length()) {
      result.add(s.substring(pos, s.length()));
    }
    return result;
  }

  @NotNull
  public static Iterable<String> tokenize(@NotNull String s, @NotNull String separators) {
    final com.intellij.util.text.StringTokenizer tokenizer = new com.intellij.util.text.StringTokenizer(s, separators);
    return new Iterable<String>() {
      public Iterator<String> iterator() {
        return new Iterator<String>() {
          public boolean hasNext() {
            return tokenizer.hasMoreTokens();
          }

          public String next() {
            return tokenizer.nextToken();
          }

          public void remove() {
            throw new UnsupportedOperationException();
          }
        };
      }
    };
  }

  @NotNull
  public static List<String> getWordsIn(@NotNull String text) {
    List<String> result = new SmartList<String>();
    int start = -1;
    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);
      boolean isIdentifierPart = Character.isJavaIdentifierPart(c);
      if (isIdentifierPart && start == -1) {
        start = i;
      }
      if (isIdentifierPart && i == text.length() - 1 && start != -1) {
        result.add(text.substring(start, i + 1));
      }
      else if (!isIdentifierPart && start != -1) {
        result.add(text.substring(start, i));
        start = -1;
      }
    }
    return result;
  }

  @NotNull
  public static List<TextRange> getWordIndicesIn(@NotNull String text) {
    List<TextRange> result = new SmartList<TextRange>();
    int start = -1;
    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);
      boolean isIdentifierPart = Character.isJavaIdentifierPart(c);
      if (isIdentifierPart && start == -1) {
        start = i;
      }
      if (isIdentifierPart && i == text.length() - 1 && start != -1) {
        result.add(new TextRange(start, i + 1));
      }
      else if (!isIdentifierPart && start != -1) {
        result.add(new TextRange(start, i));
        start = -1;
      }
    }
    return result;
  }

  @NotNull
  public static String join(@NotNull final String[] strings, @NotNull final String separator) {
    return join(strings, 0, strings.length, separator);
  }

  @NotNull
  public static String join(@NotNull final String[] strings, int startIndex, int endIndex, @NotNull final String separator) {
    final StringBuilder result = new StringBuilder();
    for (int i = startIndex; i < endIndex; i++) {
      if (i > startIndex) result.append(separator);
      result.append(strings[i]);
    }
    return result.toString();
  }

  @NotNull
  public static String[] zip(@NotNull String[] strings1, @NotNull String[] strings2, String separator) {
    if (strings1.length != strings2.length) throw new IllegalArgumentException();

    String[] result = ArrayUtil.newStringArray(strings1.length);
    for (int i = 0; i < result.length; i++) {
      result[i] = strings1[i] + separator + strings2[i];
    }

    return result;
  }

  @NotNull
  public static String[] surround(@NotNull String[] strings1, String prefix, String suffix) {
    String[] result = ArrayUtil.newStringArray(strings1.length);
    for (int i = 0; i < result.length; i++) {
      result[i] = prefix + strings1[i] + suffix;
    }

    return result;
  }

  @NotNull
  public static <T> String join(@NotNull T[] items, @NotNull Function<T, String> f, @NotNull @NonNls String separator) {
    return join(Arrays.asList(items), f, separator);
  }

  @NotNull
  public static <T> String join(@NotNull Collection<T> items, @NotNull Function<T, String> f, @NotNull @NonNls String separator) {
    if (items.isEmpty()) return "";
    return join((Iterable<T>)items, f, separator);
  }

  @NotNull
  public static <T> String join(@NotNull Iterable<T> items, @NotNull Function<T, String> f, @NotNull @NonNls String separator) {
    final StringBuilder result = new StringBuilder();
    for (T item : items) {
      String string = f.fun(item);
      if (string != null && string.length() != 0) {
        if (result.length() != 0) result.append(separator);
        result.append(string);
      }
    }
    return result.toString();
  }

  @NotNull
  public static String join(@NotNull Collection<? extends String> strings, @NotNull final String separator) {
    final StringBuilder result = new StringBuilder();
    for (String string : strings) {
      if (string != null && string.length() != 0) {
        if (result.length() != 0) result.append(separator);
        result.append(string);
      }
    }
    return result.toString();
  }

  @NotNull
  public static String join(@NotNull final int[] strings, @NotNull final String separator) {
    final StringBuilder result = new StringBuilder();
    for (int i = 0; i < strings.length; i++) {
      if (i > 0) result.append(separator);
      result.append(strings[i]);
    }
    return result.toString();
  }

  @NotNull
  public static String stripQuotesAroundValue(@NotNull String text) {
    if (startsWithChar(text, '\"') || startsWithChar(text, '\'')) text = text.substring(1);
    if (endsWithChar(text, '\"') || endsWithChar(text, '\'')) text = text.substring(0, text.length() - 1);
    return text;
  }

  public static boolean isQuotedString(@NotNull String text) {
    return startsWithChar(text, '\"') && endsWithChar(text, '\"')
           || startsWithChar(text, '\'') && endsWithChar(text, '\'');
  }

  /**
   * Formats the specified file size as a string.
   *
   * @param fileSize the size to format.
   * @return the size formatted as a string.
   * @since 5.0.1
   */
  @NotNull
  public static String formatFileSize(final long fileSize) {
    if (fileSize < 0x400) {
      return CommonBundle.message("format.file.size.bytes", fileSize);
    }
    if (fileSize < 0x100000) {
      long kbytes = fileSize * 100 / 1024;
      final String kbs = kbytes / 100 + "." + formatMinor(kbytes % 100);
      return CommonBundle.message("format.file.size.kbytes", kbs);
    }
    long mbytes = fileSize * 100 / 1024 / 1024;
    final String size = mbytes / 100 + "." + formatMinor(mbytes % 100);
    return CommonBundle.message("format.file.size.mbytes", size);
  }

  @NotNull
  public static String formatDate(long timestamp, DateFormat format) {
    long minDelta = (new Date().getTime() - timestamp) / (1000 * 60);

    if (minDelta < 2) {
      return CommonBundle.message("format.date.momentsAgo");
    }
    if (minDelta < 10) {
      return CommonBundle.message("format.date.fewMinutesAgo");
    }

    if (minDelta <= 30) {
      return CommonBundle.message("format.date.last30Minutes");
    }

    int hoursDelta = Math.round(minDelta / 60f);
    if (hoursDelta <= 1) {
      return CommonBundle.message("format.date.lastHour");
    }
    if (hoursDelta < 5) {
      return CommonBundle.message("format.date.hoursAgo", hoursDelta);
    }
    return format.format(timestamp);
  }

  @NotNull
  private static String formatMinor(long number) {
    if (number > 0L && number <= 9L) {
      return "0" + number;
    }
    return String.valueOf(number);
  }

  /**
   * Returns unpluralized variant using English based heuristics like properties -> property, names -> name, children -> child.
   * Returns <code>null</code> if failed to match appropriate heuristic.
   *
   * @param name english word in plural form
   * @return name in singular form or <code>null</code> if failed to find one.
   */
  @SuppressWarnings({"HardCodedStringLiteral"})
  @Nullable
  public static String unpluralize(@NotNull final String name) {
    if (name.endsWith("sses") || name.endsWith("shes") || name.endsWith("ches") || name.endsWith("xes")) { //?
      return name.substring(0, name.length() - 2);
    }

    if (name.endsWith("ses")) {
      return name.substring(0, name.length() - 1);
    }

    if (name.endsWith("ies")) {
      return name.substring(0, name.length() - 3) + "y";
    }

    String result = stripEnding(name, "s");
    if (result != null) {
      return result;
    }

    if (name.endsWith("children")) {
      return name.substring(0, name.length() - "children".length()) + "child";
    }

    if (name.endsWith("Children") && name.length() > "Children".length()) {
      return name.substring(0, name.length() - "Children".length()) + "Child";
    }

    return null;
  }

  private static String stripEnding(@NotNull String name, @NotNull String ending) {
    if (name.endsWith(ending)) {
      if (name.equals(ending)) return name; // do not return empty string
      return name.substring(0, name.length() - 1);
    }
    return null;
  }

  public static boolean containsAlphaCharacters(@NotNull String value) {
    for (int i = 0; i < value.length(); i++) {
      if (Character.isLetter(value.charAt(i))) return true;
    }
    return false;
  }

  public static boolean containsAnyChar(@NotNull final String value, @NotNull final String chars) {
    for (int i = 0; i < chars.length(); i++) {
      if (value.indexOf(chars.charAt(i)) != -1) return true;
    }

    return false;
  }

  public static String firstLetterToUpperCase(String displayString) {
    if (displayString == null || displayString.length() == 0) return displayString;
    char firstChar = displayString.charAt(0);
    char uppedFirstChar = toUpperCase(firstChar);

    if (uppedFirstChar == firstChar) return displayString;

    char[] buffer = displayString.toCharArray();
    buffer[0] = uppedFirstChar;
    return new String(buffer);
  }

  /**
   * Strip out all characters not accepted by given filter
   *
   * @param s      e.g. "/n    my string "
   * @param filter e.g. {@link CharFilter#NOT_WHITESPACE_FILTER}
   * @return stripped string e.g. "mystring"
   */
  @NotNull
  public static String strip(@NotNull final String s, @NotNull CharFilter filter) {
    StringBuilder result = new StringBuilder(s.length());
    for (int i = 0; i < s.length(); i++) {
      char ch = s.charAt(i);
      if (filter.accept(ch)) {
        result.append(ch);
      }
    }
    return result.toString();
  }

  public static List<String> findMatches(String s, Pattern pattern) {
    List<String> result = new SmartList<String>();
    Matcher m = pattern.matcher(s);
    while (m.find()) {
      if (m.groupCount() > 0) {
        result.add(m.group(1));
      }
    }
    return result;
  }

  /**
   * Find position of the first charachter accepted by given filter
   *
   * @param s      the string to search
   * @param filter
   * @return position of the first charachter accepted or -1 if not found
   */
  public static int findFirst(@NotNull final String s, @NotNull CharFilter filter) {
    for (int i = 0; i < s.length(); i++) {
      char ch = s.charAt(i);
      if (filter.accept(ch)) {
        return i;
      }
    }
    return -1;
  }

  @NotNull
  public static String replaceSubstring(@NotNull String string, @NotNull TextRange range, @NotNull String replacement) {
    return range.replace(string, replacement);
  }

  public static boolean startsWith(@NotNull CharSequence text, @NotNull CharSequence prefix) {
    int l1 = text.length();
    int l2 = prefix.length();
    if (l1 < l2) return false;

    for (int i = 0; i < l2; i++) {
      if (text.charAt(i) != prefix.charAt(i)) return false;
    }

    return true;
  }

  public static boolean endsWith(@NotNull CharSequence text, @NotNull CharSequence suffix) {
    int l1 = text.length();
    int l2 = suffix.length();
    if (l1 < l2) return false;

    for (int i = l1 - 1; i >= l1 - l2; i--) {
      if (text.charAt(i) != suffix.charAt(i + l2 - l1)) return false;
    }

    return true;
  }

  @NotNull
  public static String commonPrefix(@NotNull String s1, @NotNull String s2) {
    return s1.substring(0, commonPrefixLength(s1, s2));
  }

  public static int commonPrefixLength(@NotNull CharSequence s1, @NotNull CharSequence s2) {
    int i;
    for (i = 0; i < s1.length() && i < s2.length(); i++) {
      if (s1.charAt(i) != s2.charAt(i)) {
        break;
      }
    }
    return i;
  }

  @NotNull
  public static String commonSuffix(@NotNull String s1, @NotNull String s2) {
    return s1.substring(s1.length() - commonSuffixLength(s1, s2));
  }

  public static int commonSuffixLength(@NotNull CharSequence s1, @NotNull CharSequence s2) {
    if (s1.length() == 0 || s2.length() == 0) return 0;
    int i;
    for (i = 0; i < s1.length() && i < s2.length(); i++) {
      if (s1.charAt(s1.length() - i - 1) != s2.charAt(s2.length() - i - 1)) {
        break;
      }
    }
    return i;
  }

  /**
   * Allows to answer if target symbol is contained at given char sequence at <code>[start; end)</code> interval.
   *
   * @param s     target char sequence to check
   * @param start start offset to use within the given char sequence (inclusive)
   * @param end   end offset to use within the given char sequence (exclusive)
   * @param c     target symbol to check
   * @return <code>true</code> if given symbol is contained at the target range of the given char sequence;
   *         <code>false</code> otherwise
   */
  public static boolean contains(CharSequence s, int start, int end, char c) {
    return indexOf(s, c, start, end) >= 0;
  }

  public static int indexOf(@NotNull CharSequence s, char c) {
    return indexOf(s, c, 0, s.length());
  }

  public static int indexOf(@NotNull CharSequence s, char c, int start, int end) {
    for (int i = start; i < end; i++) {
      if (s.charAt(i) == c) return i;
    }
    return -1;
  }

  /**
   * Allows to retrieve index of last occurrence of the given symbols at <code>[start; end)</code> sub-sequence of the given text.
   *
   * @param s     target text
   * @param c     target symbol which last occurrence we want to check
   * @param start start offset of the target text (inclusive)
   * @param end   end offset of the target text (exclusive)
   * @return index of the last occurrence of the given symbol at the target sub-sequence of the given text if any;
   *         <code>-1</code> otherwise
   */
  public static int lastIndexOf(@NotNull CharSequence s, char c, int start, int end) {
    for (int i = end - 1; i >= start; i--) {
      if (s.charAt(i) == c) return i;
    }
    return -1;
  }

  @NotNull
  public static String first(@NotNull String text, final int length, final boolean appendEllipsis) {
    return text.length() > length ? text.substring(0, length) + (appendEllipsis ? "..." : "") : text;
  }

  @NotNull
  public static CharSequence first(@NotNull CharSequence text, final int length, final boolean appendEllipsis) {
    return text.length() > length ? text.subSequence(0, length) + (appendEllipsis ? "..." : "") : text;
  }

  @NotNull
  public static CharSequence last(@NotNull CharSequence text, final int length, boolean prependEllipsis) {
    return text.length() > length ? (prependEllipsis ? "..." : "") + text.subSequence(text.length() - length, text.length()) : text;
  }

  @NotNull
  public static String escapeQuotes(@NotNull final String str) {
    int idx = str.indexOf('"');
    if (idx < 0) return str;
    StringBuilder buf = new StringBuilder(str);
    while (idx < buf.length()) {
      if (buf.charAt(idx) == '"') {
        buf.replace(idx, idx + 1, "\\\"");
        idx += 2;
      }
      else {
        idx += 1;
      }
    }
    return buf.toString();
  }

  @NonNls private static final String[] REPLACES_REFS = {"&lt;", "&gt;", "&amp;", "&apos;", "&quot;"};
  @NonNls private static final String[] REPLACES_DISP = {"<", ">", "&", "'", "\""};

  public static String unescapeXml(final String text) {
    if (text == null) return null;
    return replace(text, REPLACES_REFS, REPLACES_DISP);
  }

  public static String escapeXml(final String text) {
    if (text == null) return null;
    return replace(text, REPLACES_DISP, REPLACES_REFS);
  }

  @NotNull
  public static String escapeToRegexp(@NotNull String text) {
    @NonNls StringBuilder result = new StringBuilder();
    for (int i = 0; i < text.length(); i++) {
      final char c = text.charAt(i);
      if (c == ' ' || Character.isLetter(c) || Character.isDigit(c) || c == '_') {
        result.append(c);
      }
      else if (c == '\n') {
        result.append("\\n");
      }
      else {
        result.append('\\').append(c);
      }
    }

    return result.toString();
  }

  @NotNull
  public static String replace(@NotNull String text, @NotNull String[] from, @NotNull String[] to) {
    final StringBuilder result = new StringBuilder(text.length());
    replace:
    for (int i = 0; i < text.length(); i++) {
      for (int j = 0; j < from.length; j += 1) {
        String toReplace = from[j];
        String replaceWith = to[j];

        final int len = toReplace.length();
        if (text.regionMatches(i, toReplace, 0, len)) {
          result.append(replaceWith);
          i += len - 1;
          continue replace;
        }
      }
      result.append(text.charAt(i));
    }
    return result.toString();
  }

  @NotNull
  public static String[] filterEmptyStrings(@NotNull String[] strings) {
    int emptyCount = 0;
    for (String string : strings) {
      if (string == null || string.length() == 0) emptyCount++;
    }
    if (emptyCount == 0) return strings;

    String[] result = ArrayUtil.newStringArray(strings.length - emptyCount);
    int count = 0;
    for (String string : strings) {
      if (string == null || string.length() == 0) continue;
      result[count++] = string;
    }

    return result;
  }

  public static int countNewLines(@NotNull CharSequence text) {
    return countChars(text, '\n');
  }

  public static int countChars(@NotNull CharSequence text, char c) {
    int count = 0;

    for (int i = 0; i < text.length(); ++i) {
      final char ch = text.charAt(i);
      if (ch == c) {
        ++count;
      }
    }
    return count;
  }

  @NotNull
  public static String capitalsOnly(@NotNull String s) {
    StringBuilder b = new StringBuilder();
    for (int i = 0; i < s.length(); i++) {
      if (Character.isUpperCase(s.charAt(i))) {
        b.append(s.charAt(i));
      }
    }

    return b.toString();
  }

  // returns null if any of args is null
  @Nullable
  public static String joinOrNull(@NotNull String... args) {
    StringBuilder r = new StringBuilder();
    for (String arg : args) {
      if (arg == null) return null;
      r.append(arg);
    }
    return r.toString();
  }

  public static String getPropertyName(@NonNls @NotNull String methodName) {
    if (methodName.startsWith("get")) {
      return Introspector.decapitalize(methodName.substring(3));
    }
    else if (methodName.startsWith("is")) {
      return Introspector.decapitalize(methodName.substring(2));
    }
    else if (methodName.startsWith("set")) {
      return Introspector.decapitalize(methodName.substring(3));
    }
    else {
      return null;
    }
  }

  public static boolean isJavaIdentifierStart(char c) {
    return c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z' || Character.isJavaIdentifierStart(c);
  }

  public static boolean isJavaIdentifierPart(char c) {
    return c >= '0' && c <= '9' || isJavaIdentifierStart(c);
  }

  public static boolean isJavaIdentifier(@NotNull String text) {
    int len = text.length();
    if (len == 0) return false;

    if (!isJavaIdentifierStart(text.charAt(0))) return false;

    for (int i = 1; i < len; i++) {
      if (!isJavaIdentifierPart(text.charAt(i))) return false;
    }

    return true;
  }

  @NotNull
  public static String shiftIndentInside(@NotNull String initial, final int i, boolean shiftEmptyLines) throws IOException {
    StringBuilder result = new StringBuilder(initial.length());
    LineReader reader = new LineReader(new ByteArrayInputStream(initial.getBytes()));
    boolean first = true;
    for (byte[] line : reader.readLines()) {
      try {
        if (!first) result.append('\n');
        if (line.length > 0 || shiftEmptyLines) {
          repeatSymbol(result, ' ', i);
        }
        result.append(new String(line));
      }
      finally {
        first = false;
      }
    }

    return result.toString();
  }

  /**
   * Escape property name or key in property file. Unicode characters are escaped as well.
   *
   * @param input an input to escape
   * @param isKey if true, they rules for key escaping are applied. The leading space is escaped in that case.
   * @return an escaped string
   */
  @NotNull
  public static String escapeProperty(@NotNull String input, final boolean isKey) {
    final StringBuilder escaped = new StringBuilder();
    for (int i = 0; i < input.length(); i++) {
      final char ch = input.charAt(i);
      switch (ch) {
        case ' ':
          if (isKey && i == 0) {
            // only the leading space has to be escaped
            escaped.append('\\');
          }
          escaped.append(' ');
          break;
        case '\t':
          escaped.append("\\t");
          break;
        case '\r':
          escaped.append("\\r");
          break;
        case '\n':
          escaped.append("\\n");
          break;
        case '\f':
          escaped.append("\\f");
          break;
        case '\\':
        case '#':
        case '!':
        case ':':
        case '=':
          escaped.append('\\');
          escaped.append(ch);
          break;
        default:
          if (20 < ch && ch < 0x7F) {
            escaped.append(ch);
          }
          else {
            escaped.append("\\u");
            escaped.append(Character.forDigit((ch >> 12) & 0xF, 16));
            escaped.append(Character.forDigit((ch >> 8) & 0xF, 16));
            escaped.append(Character.forDigit((ch >> 4) & 0xF, 16));
            escaped.append(Character.forDigit((ch) & 0xF, 16));
          }
          break;
      }
    }
    return escaped.toString();
  }

  public static String getQualifiedName(String packageName, String className) {
    if (packageName == null || packageName.length() == 0) {
      return className;
    }
    return packageName + '.' + className;
  }

  public static int compareVersionNumbers(String v1, String v2) {
    if (v1 == null && v2 == null) {
      return 0;
    }
    else if (v1 == null) {
      return -1;
    }
    else if (v2 == null) return 1;

    String[] part1 = v1.split("[\\.\\_\\-]");
    String[] part2 = v2.split("[\\.\\_\\-]");

    int idx = 0;
    for (; idx < part1.length && idx < part2.length; idx++) {
      String p1 = part1[idx];
      String p2 = part2[idx];

      int cmp;
      if (p1.matches("\\d+") && p2.matches("\\d+")) {
        cmp = new Integer(p1).compareTo(new Integer(p2));
      }
      else {
        cmp = part1[idx].compareTo(part2[idx]);
      }
      if (cmp != 0) return cmp;
    }

    if (part1.length == part2.length) {
      return 0;
    }
    else if (part1.length > idx) {
      return 1;
    }
    else {
      return -1;
    }
  }

  public static int parseInt(final String string, final int defaultValue) {
    try {
      return Integer.parseInt(string);
    }
    catch (Exception e) {
      return defaultValue;
    }
  }

  public static double parseDouble(final String string, final double defaultValue) {
    try {
      return Double.parseDouble(string);
    }
    catch (Exception e) {
      return defaultValue;
    }
  }

  public static int getOccurenceCount(@NotNull String text, final char c) {
    int res = 0;
    int i = 0;
    while (i < text.length()) {
      i = text.indexOf(c, i);
      if (i >= 0) {
        res++;
        i++;
      }
      else {
        break;
      }
    }
    return res;
  }

  @NotNull
  public static String fixVariableNameDerivedFromPropertyName(@NotNull String name) {
    char c = name.charAt(0);
    if (isVowel(c)) {
      return "an" + Character.toUpperCase(c) + name.substring(1);
    }
    return "a" + Character.toUpperCase(c) + name.substring(1);
  }

  public static String sanitizeJavaIdentifier(String name) {
    final StringBuilder result = new StringBuilder();

    for (int i = 0; i < name.length(); i++) {
      final char ch = name.charAt(i);
      if (Character.isLetterOrDigit(ch)) {
        result.append(ch);
      }
    }

    return result.toString();
  }

  public static void assertValidSeparators(@NotNull CharSequence s) {
    for (int i = 0; i < s.length(); i++) {
      if (s.charAt(i) == '\r') {
        String context = String.valueOf(last(s.subSequence(0, i), 10, true)) + first(s.subSequence(i, s.length()), 10, true);
        context = escapeStringCharacters(context);
        LOG.error("Wrong line separators: '" + context + "' at offset " + i);
      }
    }
  }

  @NotNull
  public static String tail(@NotNull String s, final int idx) {
    return idx >= s.length() ? "" : s.substring(idx, s.length());
  }

  /**
   * Splits string by lines.
   *
   * @param string String to split
   * @return array of strings
   */
  public static String[] splitByLines(final String string) {
    return EOL_SPLIT_PATTERN.split(string);
  }

  public static List<Pair<String, Integer>> getWordsWithOffset(String s) {
    List<Pair<String, Integer>> res = Lists.newArrayList();
    s += " ";
    StringBuilder name = new StringBuilder();
    int startInd = -1;
    for (int i = 0; i < s.length(); i++) {
      if (Character.isWhitespace(s.charAt(i))) {
        if (name.length() > 0) {
          res.add(Pair.create(name.toString(), startInd));
          name.setLength(0);
          startInd = -1;
        }
      }
      else {
        if (startInd == -1) {
          startInd = i;
        }
        name.append(s.charAt(i));
      }
    }
    return res;
  }
}
