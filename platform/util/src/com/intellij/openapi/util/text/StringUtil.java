/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

import com.intellij.CommonBundle;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.util.*;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.text.CharArrayCharSequence;
import com.intellij.util.text.CharArrayUtil;
import com.intellij.util.text.LineReader;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.beans.Introspector;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

//TeamCity inherits StringUtil: do not add private constructors!!!
@SuppressWarnings({"UtilityClassWithoutPrivateConstructor", "MethodOverridesStaticMethodOfSuperclass"})
public class StringUtil extends StringUtilRt {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.util.text.StringUtil");

  @NonNls private static final String VOWELS = "aeiouy";
  @NonNls private static final Pattern EOL_SPLIT_PATTERN = Pattern.compile(" *(\r|\n|\r\n)+ *");

  public static final NotNullFunction<String, String> QUOTER = new NotNullFunction<String, String>() {
    @Override
    @NotNull
    public String fun(String s) {
      return "\"" + s + "\"";
    }
  };

  public static final NotNullFunction<String, String> SINGLE_QUOTER = new NotNullFunction<String, String>() {
    @Override
    @NotNull
    public String fun(String s) {
      return "'" + s + "'";
    }
  };

  @NotNull
  public static List<String> getWordsInStringLongestFirst(@NotNull String find) {
    List<String> words = getWordsIn(find);
    // hope long words are rare
    Collections.sort(words, new Comparator<String>() {
      @Override
      public int compare(@NotNull final String o1, @NotNull final String o2) {
        return o2.length() - o1.length();
      }
    });
    return words;
  }

  @NotNull
  public static String escapePattern(@NotNull final String text) {
    return replace(replace(text, "'", "''"), "{", "'{'");
  }

  @NotNull
  public static <T> Function<T, String> createToStringFunction(Class<T> cls) {
    return new Function<T, String>() {
      @Override
      public String fun(@NotNull T o) {
        return o.toString();
      }
    };
  }

  @NotNull public static Function<String, String> TRIMMER = new Function<String, String>() {
    @Nullable
    @Override
    public String fun(@Nullable String s) {
      return s == null ? null : s.trim();
    }
  };

  @NotNull public static String replace(@NonNls @NotNull String text, @NonNls @NotNull String oldS, @NonNls @Nullable String newS) {
    return replace(text, oldS, newS, false);
  }

  @NotNull
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

  @NotNull
  public static String replaceChar(@NotNull String buffer, char oldChar, char newChar) {
    StringBuilder newBuffer = null;
    for (int i = 0; i < buffer.length(); i++) {
      char c = buffer.charAt(i);
      if (c == oldChar) {
        if (newBuffer == null) {
          newBuffer = new StringBuilder(buffer.length());
          newBuffer.append(buffer, 0, i);
        }

        newBuffer.append(newChar);
      }
      else if (newBuffer != null) {
        newBuffer.append(c);
      }
    }
    return newBuffer == null ? buffer : newBuffer.toString();
  }

  @NotNull
  public static String replace(@NotNull final String text, @NotNull final String oldS, @Nullable final String newS, boolean ignoreCase) {
    if (text.length() < oldS.length()) return text;

    final String text1 = ignoreCase ? text.toLowerCase() : text;
    final String oldS1 = ignoreCase ? oldS.toLowerCase() : oldS;
    StringBuilder newText = null;
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
        if (newText == null) newText = new StringBuilder();
        newText.append(text, i, i1);
        newText.append(newS);
        i = i1 + oldS.length();
      }
    }
    return newText != null ? newText.toString() : "";
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

  public static int indexOfIgnoreCase(@NotNull String where, char what, int fromIndex) {
    int sourceCount = where.length();

    if (fromIndex >= sourceCount) {
      return -1;
    }

    if (fromIndex < 0) {
      fromIndex = 0;
    }

    int max = sourceCount - 1;

    for (int i = fromIndex; i <= max; i++) {
      /* Look for first character. */
      if (!charsEqualIgnoreCase(where.charAt(i), what)) {
        while (++i <= max && !charsEqualIgnoreCase(where.charAt(i), what)) ;
      }

      /* Found first character, now look at the rest of v2 */
      if (i <= max) {
        return i;
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

  public static String stripHtml(String html, boolean convertBreaks) {
    if (convertBreaks) {
      html = html.replaceAll("<br/?>", "\n\n");
    }

    return html.replaceAll("<(.|\n)*?>", "");
  }

  @Nullable
  public static String toLowerCase(@Nullable final String str) {
    //noinspection ConstantConditions
    return str == null ? null : str.toLowerCase();
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
      if (isLineBreak(c)) return true;
    }
    return false;
  }

  public static boolean isLineBreak(char c) {
    return c == '\n' || c == '\r';
  }

  public static boolean endsWithLineBreak(@NotNull CharSequence text) {
    int len = text.length();
    return len > 0 && isLineBreak(text.charAt(len - 1));
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
    StringBuilder buffer = null;
    for (int i = 0; i < s.length(); i++) {
      char prevChar = i == 0 ? ' ' : s.charAt(i - 1);
      char currChar = s.charAt(i);
      if (!Character.isLetterOrDigit(prevChar) && prevChar != '\'') {
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
                buffer = new StringBuilder(s);
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

  @NonNls private static final String[] ourPrepositions = {"at", "the", "and", "not", "if", "a", "or", "to", "in", "on", "into", "by"};


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

  @NotNull
  public static NotNullFunction<String, String> escaper(final boolean escapeSlash, @Nullable final String additionalChars) {
    return new NotNullFunction<String, String>() {
      @NotNull
      @Override
      public String fun(@NotNull String dom) {
        final StringBuilder builder = StringBuilderSpinAllocator.alloc();
        try {
          StringUtil.escapeStringCharacters(dom.length(), dom, additionalChars, escapeSlash, builder);
          return builder.toString();
        }
        finally {
          StringBuilderSpinAllocator.dispose(builder);
        }
      }
    };
  }


  public static void escapeStringCharacters(int length, @NotNull String str, @NotNull @NonNls StringBuilder buffer) {
    escapeStringCharacters(length, str, "\"", buffer);
  }

  @NotNull
  public static StringBuilder escapeStringCharacters(int length,
                                                     @NotNull String str,
                                                     @Nullable String additionalChars,
                                                     @NotNull @NonNls StringBuilder buffer) {
    return escapeStringCharacters(length, str, additionalChars, true, buffer);
  }

  @NotNull
  public static StringBuilder escapeStringCharacters(int length,
                                                     @NotNull String str,
                                                     @Nullable String additionalChars,
                                                     boolean escapeSlash,
                                                     @NotNull @NonNls StringBuilder buffer) {
    char prev = 0;
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

        default:
          if (escapeSlash && ch == '\\') {
            buffer.append("\\\\");
          }
          else if (additionalChars != null && additionalChars.indexOf(ch) > -1 && (escapeSlash || prev != '\\')) {
            buffer.append("\\").append(ch);
          }
          else if (Character.isISOControl(ch)) {
            String hexCode = StringUtilRt.toUpperCase(Integer.toHexString(ch));
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
      prev = ch;
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
    char c;
    if (s.length() <= 1 || (c = s.charAt(0)) != '"' && c != '\'' || s.charAt(s.length() - 1) != c) {
      return s;
    }
    return s.substring(1, s.length() - 1);
  }

  /**
   * This is just an optimized version of Matcher.quoteReplacement
   */
  @NotNull
  public static String quoteReplacement(@NotNull String s) {
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

    if (suggestion.equals("this")) {
      return "these";
    }
    if (suggestion.equals("This")) {
      return "These";
    }

    if (endsWithIgnoreCase(suggestion, "s") || endsWithIgnoreCase(suggestion, "x") || endsWithIgnoreCase(suggestion, "ch")) {
      return suggestion + "es";
    }

    int len = suggestion.length();
    if (endsWithIgnoreCase(suggestion, "y") && len > 1 && !isVowel(toLowerCase(suggestion.charAt(len - 2)))) {
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
    if (s.isEmpty()) return s;
    if (s.length() == 1) return StringUtilRt.toUpperCase(s);

    // Optimization
    if (Character.isUpperCase(s.charAt(0))) return s;
    return toUpperCase(s.charAt(0)) + s.substring(1);
  }

  public static boolean isCapitalized(@Nullable String s) {
    return s != null && !s.isEmpty() && Character.isUpperCase(s.charAt(0));
  }

  @NotNull
  public static String capitalizeWithJavaBeanConvention(@NotNull String s) {
    if (s.length() > 1 && Character.isUpperCase(s.charAt(1))) {
      return s;
    }
    return capitalize(s);
  }

  public static int stringHashCode(@NotNull CharSequence chars) {
    if (chars instanceof String) return chars.hashCode();
    if (chars instanceof CharSequenceWithStringHash) return chars.hashCode();
    if (chars instanceof CharArrayCharSequence) return chars.hashCode();

    return stringHashCode(chars, 0, chars.length());
  }

  public static int stringHashCode(@NotNull CharSequence chars, int from, int to) {
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

  public static int stringHashCodeInsensitive(@NotNull char[] chars, int from, int to) {
    int h = 0;
    for (int off = from; off < to; off++) {
      h = 31 * h + toLowerCase(chars[off]);
    }
    return h;
  }

  public static int stringHashCodeInsensitive(@NotNull CharSequence chars, int from, int to) {
    int h = 0;
    for (int off = from; off < to; off++) {
      h = 31 * h + toLowerCase(chars.charAt(off));
    }
    return h;
  }

  public static int stringHashCodeInsensitive(@NotNull CharSequence chars) {
    return stringHashCodeInsensitive(chars, 0, chars.length());
  }

  /**
   * Equivalent to testee.startsWith(firstPrefix + secondPrefix) but avoids creating an object for concatenation.
   *
   * @param testee
   * @param firstPrefix
   * @param secondPrefix
   * @return
   */
  public static boolean startsWithConcatenationOf(@NotNull String testee, @NotNull String firstPrefix, @NotNull String secondPrefix) {
    int l1 = firstPrefix.length();
    int l2 = secondPrefix.length();
    if (testee.length() < l1 + l2) return false;
    return testee.startsWith(firstPrefix) && testee.regionMatches(l1, secondPrefix, 0, l2);
  }

  @NotNull
  public static String trimEnd(@NotNull String s, @NonNls @NotNull String suffix) {
    if (s.endsWith(suffix)) {
      return s.substring(0, s.length() - suffix.length());
    }
    return s;
  }

  @NotNull
  public static String trimLog(@NotNull final String text, final int limit) {
    if (limit > 5 && text.length() > limit) {
      return text.substring(0, limit - 5) + " ...\n";
    }
    return text;
  }

  @NotNull
  public static String trimLeading(@NotNull String string) {
    int index = 0;
    while (index < string.length() && Character.isWhitespace(string.charAt(index))) index++;
    return string.substring(index);
  }

  @NotNull
  public static String trimTrailing(@NotNull String string) {
    int index = string.length() - 1;
    while (index >= 0 && Character.isWhitespace(string.charAt(index))) index--;
    return string.substring(0, index + 1);
  }

  public static boolean startsWithChar(@Nullable CharSequence s, char prefix) {
    return s != null && s.length() != 0 && s.charAt(0) == prefix;
  }

  public static boolean endsWithChar(@Nullable CharSequence s, char suffix) {
    return StringUtilRt.endsWithChar(s, suffix);
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

  public static boolean isNotEmpty(@Nullable String s) {
    return s != null && !s.isEmpty();
  }

  public static boolean isEmpty(@Nullable String s) {
    return s == null || s.isEmpty();
  }

  public static boolean isEmpty(@Nullable CharSequence cs) {
    return cs == null || cs.length() == 0;
  }

  public static int length(@Nullable CharSequence cs) {
    return cs == null ? 0 : cs.length();
  }

  @NotNull
  public static String notNullize(final String s) {
    return notNullize(s, "");
  }

  @NotNull
  public static String notNullize(@Nullable final String s, @NotNull String defaultValue) {
    return s == null ? defaultValue : s;
  }

  @Nullable
  public static String nullize(@Nullable final String s) {
    return nullize(s, false);
  }

  @Nullable
  public static String nullize(@Nullable final String s, boolean nullizeSpaces) {
    if (nullizeSpaces) {
      if (isEmpty(s)) return null;
    }
    else {
      if (isEmptyOrSpaces(s)) return null;
    }
    return s;
  }

  public static boolean isEmptyOrSpaces(@Nullable final String s) {
    return s == null || s.trim().isEmpty();
  }

  /**
   * Allows to answer if given symbol is white space, tabulation or line feed.
   *
   * @param c symbol to check
   * @return <code>true</code> if given symbol is white space, tabulation or line feed; <code>false</code> otherwise
   */
  public static boolean isWhiteSpace(char c) {
    return c == '\n' || c == '\t' || c == ' ';
  }

  @NotNull
  public static String getThrowableText(@NotNull Throwable aThrowable) {
    return ExceptionUtil.getThrowableText(aThrowable);
  }

  @NotNull
  public static String getThrowableText(@NotNull Throwable aThrowable, @NonNls @NotNull final String stackFrameSkipPattern) {
    return ExceptionUtil.getThrowableText(aThrowable, stackFrameSkipPattern);
  }

  @Nullable
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
  public static String repeat(final String s, final int count) {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < count; i++) {
      sb.append(s);
    }
    return sb.toString();
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
    return split(s, separator, excludeSeparator, true);
  }

  @NotNull
  public static List<String> split(@NotNull String s, @NotNull String separator,
                                   boolean excludeSeparator, boolean excludeEmptyStrings) {
    if (separator.isEmpty()) {
      return Collections.singletonList(s);
    }
    List<String> result = new ArrayList<String>();
    int pos = 0;
    while (true) {
      int index = s.indexOf(separator, pos);
      if (index == -1) break;
      final int nextPos = index + separator.length();
      String token = s.substring(pos, excludeSeparator ? index : nextPos);
      if (!token.isEmpty() || !excludeEmptyStrings) {
        result.add(token);
      }
      pos = nextPos;
    }
    if (pos < s.length() || (!excludeEmptyStrings && pos == s.length())) {
      result.add(s.substring(pos, s.length()));
    }
    return result;
  }

  @NotNull
  public static Iterable<String> tokenize(@NotNull String s, @NotNull String separators) {
    final com.intellij.util.text.StringTokenizer tokenizer = new com.intellij.util.text.StringTokenizer(s, separators);
    return new Iterable<String>() {
      @NotNull
      @Override
      public Iterator<String> iterator() {
        return new Iterator<String>() {
          @Override
          public boolean hasNext() {
            return tokenizer.hasMoreTokens();
          }

          @Override
          public String next() {
            return tokenizer.nextToken();
          }

          @Override
          public void remove() {
            throw new UnsupportedOperationException();
          }
        };
      }
    };
  }

  @NotNull
  public static Iterable<String> tokenize(@NotNull final StringTokenizer tokenizer) {
    return new Iterable<String>() {
      @NotNull
      @Override
      public Iterator<String> iterator() {
        return new Iterator<String>() {
          @Override
          public boolean hasNext() {
            return tokenizer.hasMoreTokens();
          }

          @Override
          public String next() {
            return tokenizer.nextToken();
          }

          @Override
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

  public static String join(@NotNull Iterable<?> items, @NotNull @NonNls String separator) {
    StringBuilder result = new StringBuilder();
    for (Object item : items) {
      result.append(item).append(separator);
    }
    if (result.length() > 0) {
      result.setLength(result.length() - 1);
    }
    return result.toString();
  }
  
  @NotNull
  public static <T> String join(@NotNull Iterable<T> items, @NotNull Function<T, String> f, @NotNull @NonNls String separator) {
    final StringBuilder result = new StringBuilder();
    for (T item : items) {
      String string = f.fun(item);
      if (string != null && !string.isEmpty()) {
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
      if (string != null && !string.isEmpty()) {
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
  public static String join(@Nullable final String... strings) {
    if (strings == null || strings.length == 0) return "";

    final StringBuilder builder = StringBuilderSpinAllocator.alloc();
    try {
      for (final String string : strings) {
        builder.append(string);
      }
      return builder.toString();
    }
    finally {
      StringBuilderSpinAllocator.dispose(builder);
    }
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
      if (name.endsWith("cookies") || name.endsWith("Cookies")) {
        return name.substring(0, name.length() - "ookies".length()) + "ookie";
      }

      return name.substring(0, name.length() - 3) + "y";
    }

    if (name.endsWith("leaves") || name.endsWith("Leaves")) {
      return name.substring(0, name.length() - "eaves".length()) + "eaf";
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

  @Nullable
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
      if (containsChar(value, chars.charAt(i))) return true;
    }

    return false;
  }

  public static boolean containsChar(@NotNull final String value, final char ch) {
    return value.indexOf(ch) >= 0;
  }

  /**
   * @deprecated use #capitalize(String)
   */
  @Nullable
  public static String firstLetterToUpperCase(@Nullable final String displayString) {
    if (displayString == null || displayString.isEmpty()) return displayString;
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
  public static String strip(@NotNull final String s, @NotNull final CharFilter filter) {
    final StringBuilder result = new StringBuilder(s.length());
    for (int i = 0; i < s.length(); i++) {
      char ch = s.charAt(i);
      if (filter.accept(ch)) {
        result.append(ch);
      }
    }
    return result.toString();
  }

  @NotNull
  public static List<String> findMatches(@NotNull String s, @NotNull Pattern pattern) {
    return findMatches(s, pattern, 1);
  }

  @NotNull
  public static List<String> findMatches(@NotNull String s, @NotNull Pattern pattern, int groupIndex) {
    List<String> result = new SmartList<String>();
    Matcher m = pattern.matcher(s);
    while (m.find()) {
      String group = m.group(groupIndex);
      if (group != null) {
        result.add(group);
      }
    }
    return result;
  }

  /**
   * Find position of the first character accepted by given filter.
   *
   * @param s      the string to search
   * @param filter search filter
   * @return position of the first character accepted or -1 if not found
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

  public static boolean startsWithWhitespace(@NotNull String text) {
    return !text.isEmpty() && Character.isWhitespace(text.charAt(0));
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

  public static boolean startsWith(@NotNull CharSequence text, int startIndex, @NotNull CharSequence prefix) {
    int l1 = text.length() - startIndex;
    int l2 = prefix.length();
    if (l1 < l2) return false;

    for (int i = 0; i < l2; i++) {
      if (text.charAt(i + startIndex) != prefix.charAt(i)) return false;
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
    int minLength = Math.min(s1.length(), s2.length());
    for (i = 0; i < minLength; i++) {
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
    int s1Length = s1.length();
    int s2Length = s2.length();
    if (s1Length == 0 || s2Length == 0) return 0;
    int i;
    for (i = 0; i < s1Length && i < s2Length; i++) {
      if (s1.charAt(s1Length - i - 1) != s2.charAt(s2Length - i - 1)) {
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
  public static boolean contains(@NotNull CharSequence s, int start, int end, char c) {
    return indexOf(s, c, start, end) >= 0;
  }

  public static boolean containsWhitespaces(@Nullable CharSequence s) {
    if (s == null) return false;

    for (int i = 0; i < s.length(); i++) {
      if (Character.isWhitespace(s.charAt(i))) return true;
    }
    return false;
  }

  public static int indexOf(@NotNull CharSequence s, char c) {
    return indexOf(s, c, 0, s.length());
  }

  public static int indexOf(@NotNull CharSequence s, char c, int start) {
    return indexOf(s, c, start, s.length());
  }

  public static int indexOf(@NotNull CharSequence s, char c, int start, int end) {
    for (int i = start; i < end; i++) {
      if (s.charAt(i) == c) return i;
    }
    return -1;
  }

  public static int indexOf(@NotNull CharSequence s, char c, int start, int end, boolean caseSensitive) {
    for (int i = start; i < end; i++) {
      if (charsMatch(s.charAt(i), c, !caseSensitive)) return i;
    }
    return -1;
  }

  public static int indexOfSubstringEnd(@NotNull String text, @NotNull String subString) {
    int i = text.indexOf(subString);
    if (i == -1) return -1;
    return i + subString.length();
  }

  public static int indexOfAny(@NotNull final String s, @NotNull final String chars) {
    return indexOfAny(s, chars, 0, s.length());
  }

  public static int indexOfAny(@NotNull final String s, @NotNull final String chars, final int start, final int end) {
    for (int i = start; i < end; i++) {
      if (containsChar(chars, s.charAt(i))) return i;
    }
    return -1;
  }

  @Nullable
  public static String substringAfter(@NotNull String text, @NotNull String subString) {
    int i = text.indexOf(subString);
    if (i == -1) return null;
    return text.substring(i + subString.length());
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
  public static String first(@NotNull String text, final int maxLength, final boolean appendEllipsis) {
    return text.length() > maxLength ? text.substring(0, maxLength) + (appendEllipsis ? "..." : "") : text;
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
    final StringBuilder buf = StringBuilderSpinAllocator.alloc();
    try {
      buf.append(str);
      escapeQuotes(buf);
      return buf.toString();
    }
    finally {
      StringBuilderSpinAllocator.dispose(buf);
    }
  }

  public static void escapeQuotes(@NotNull final StringBuilder buf) {
    int idx = 0;
    while ((idx = indexOf(buf, '\"', idx)) >= 0) {
      buf.insert(idx, '\\');
      idx += 2;
    }
  }

  @NotNull
  public static String escapeSlashes(@NotNull final String str) {
    final StringBuilder buf = StringBuilderSpinAllocator.alloc();
    try {
      buf.append(str);
      escapeSlashes(buf);
      return buf.toString();
    }
    finally {
      StringBuilderSpinAllocator.dispose(buf);
    }
  }

  public static void escapeSlashes(@NotNull final StringBuilder buf) {
    int idx = 0;
    while ((idx = indexOf(buf, '/', idx)) >= 0) {
      buf.insert(idx, "\\");
      idx += 2;
    }
  }

  public static String unescapeSlashes(@NotNull final String str) {
    final StringBuilder buf = StringBuilderSpinAllocator.alloc();
    try {
      unescapeSlashes(buf, str);
      return buf.toString();
    }
    finally {
      StringBuilderSpinAllocator.dispose(buf);
    }
  }

  private static void unescapeSlashes(@NotNull StringBuilder buf, @NotNull String str) {
    final int length = str.length();
    final int last = length - 1;
    for (int i = 0; i < length; i++) {
      char ch = str.charAt(i);
      if (ch == '\\' && i != last) {
        i++;
        ch = str.charAt(i);
        if (ch != '/') buf.append('\\');
      }

      buf.append(ch);
    }
  }

  public static void quote(@NotNull final StringBuilder builder) {
    quote(builder, '\"');
  }

  public static void quote(@NotNull final StringBuilder builder, final char quotingChar) {
    builder.insert(0, quotingChar);
    builder.append(quotingChar);
  }

  @NonNls private static final String[] REPLACES_REFS = {"&lt;", "&gt;", "&amp;", "&#39;", "&quot;"};
  @NonNls private static final String[] REPLACES_DISP = {"<", ">", "&", "'", "\""};

  public static String unescapeXml(@Nullable final String text) {
    if (text == null) return null;
    return replace(text, REPLACES_REFS, REPLACES_DISP);
  }

  public static String escapeXml(@Nullable final String text) {
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
      if (string == null || string.isEmpty()) emptyCount++;
    }
    if (emptyCount == 0) return strings;

    String[] result = ArrayUtil.newStringArray(strings.length - emptyCount);
    int count = 0;
    for (String string : strings) {
      if (string == null || string.isEmpty()) continue;
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

  /**
   * @param args Strings to join.
   * @return {@code null} if any of given Strings is {@code null}.
   */
  @Nullable
  public static String joinOrNull(@NotNull String... args) {
    StringBuilder r = new StringBuilder();
    for (String arg : args) {
      if (arg == null) return null;
      r.append(arg);
    }
    return r.toString();
  }

  @Nullable
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
   * @param isKey if true, the rules for key escaping are applied. The leading space is escaped in that case.
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

  public static String getQualifiedName(@Nullable String packageName, String className) {
    if (packageName == null || packageName.isEmpty()) {
      return className;
    }
    return packageName + '.' + className;
  }

  public static int compareVersionNumbers(@Nullable String v1, @Nullable String v2) {
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

  @SuppressWarnings("UnusedDeclaration")
  /** @deprecated use {@linkplain #getOccurrenceCount(String, char)} (to remove in IDEA 13) */
  public static int getOccurenceCount(@NotNull String text, final char c) {
    return getOccurrenceCount(text, c);
  }

  public static int getOccurrenceCount(@NotNull String text, final char c) {
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

  public static int getOccurrenceCount(@NotNull String text, final String s) {
    int res = 0;
    int i = 0;
    while (i < text.length()) {
      i = text.indexOf(s, i);
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

  @NotNull
  public static String sanitizeJavaIdentifier(@NotNull String name) {
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
    char[] chars = CharArrayUtil.fromSequenceWithoutCopying(s);
    int slashRIndex = -1;

    if (chars != null) {
      for (int i = 0, len = s.length(); i < len; ++i) {
        if (chars[i] == '\r') {
          slashRIndex = i;
          break;
        }
      }
    }
    else {
      for (int i = 0, len = s.length(); i < len; i++) {
        if (s.charAt(i) == '\r') {
          slashRIndex = i;
          break;
        }
      }
    }

    if (slashRIndex != -1) {
      String context =
        String.valueOf(last(s.subSequence(0, slashRIndex), 10, true)) + first(s.subSequence(slashRIndex, s.length()), 10, true);
      context = escapeStringCharacters(context);
      LOG.error("Wrong line separators: '" + context + "' at offset " + slashRIndex);
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
    List<Pair<String, Integer>> res = ContainerUtil.newArrayList();
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

  /**
   * Implementation of "Sorting for Humans: Natural Sort Order":
   * http://www.codinghorror.com/blog/2007/12/sorting-for-humans-natural-sort-order.html
   */
  public static int naturalCompare(@NotNull String string1, @NotNull String string2) {
    return naturalCompare(string1, string2, false);
  }

  private static int naturalCompare(@NotNull String string1, @NotNull String string2, boolean caseSensitive) {
    final int string1Length = string1.length();
    final int string2Length = string2.length();
    for (int i = 0, j = 0; i < string1Length && j < string2Length; i++, j++) {
      char ch1 = string1.charAt(i);
      char ch2 = string2.charAt(j);
      if ((isDigit(ch1) || ch1 == ' ') && (isDigit(ch2) || ch2 == ' ')) {
        int startNum1 = i;
        while (ch1 == ' ' || ch1 == '0') { // skip leading spaces and zeros
          startNum1++;
          if (startNum1 >= string1Length) break;
          ch1 = string1.charAt(startNum1);
        }
        int startNum2 = j;
        while (ch2 == ' ' || ch2 == '0') {
          startNum2++;
          if (startNum2 >= string2Length) break;
          ch2 = string2.charAt(startNum2);
        }
        i = startNum1;
        j = startNum2;
        while (i < string1Length && isDigit(string1.charAt(i))) i++;
        while (j < string2Length && isDigit(string2.charAt(j))) j++;
        String digits1 = string1.substring(startNum1, i);
        String digits2 = string2.substring(startNum2, j);
        if (digits1.length() != digits2.length()) {
          return digits1.length() - digits2.length();
        }
        int numberDiff = digits1.compareTo(digits2);
        if (numberDiff != 0) {
          return numberDiff;
        }
        i--;
        j--;
        final int lengthDiff = (i - startNum1) - (j - startNum2);
        if (lengthDiff != 0) {
          return lengthDiff;
        }
        for (; startNum1 < i; startNum1++, startNum2++) {
          final int diff = string1.charAt(startNum1) - string2.charAt(startNum2);
          if (diff != 0) {
            return diff;
          }
        }
      }
      else {
        if (caseSensitive) {
          return ch1 - ch2;
        }
        else {
          // similar logic to charsMatch() below
          if (ch1 != ch2) {
            final int diff1 = Character.toUpperCase(ch1) - Character.toUpperCase(ch2);
            if (diff1 != 0) {
              final int diff2 = Character.toLowerCase(ch1) - Character.toLowerCase(ch2);
              if (diff2 != 0) {
                return diff2;
              }
            }
          }
        }
      }
    }
    if (!caseSensitive && string1Length == string2Length) {
      // do case sensitive compare if case insensitive strings are equal
      return naturalCompare(string1, string2, true);
    }
    return string1Length - string2Length;
  }

  private static boolean isDigit(char c) {
    return c >= '0' && c <= '9';
  }

  public static int compare(@Nullable String s1, @Nullable String s2, boolean ignoreCase) {
    if (s1 == s2) return 0;
    if (s1 == null) return -1;
    if (s2 == null) return 1;
    return ignoreCase ? s1.compareToIgnoreCase(s2) : s1.compareTo(s2);
  }

  public static int comparePairs(@Nullable String s1, @Nullable String t1, @Nullable String s2, @Nullable String t2, boolean ignoreCase) {
    final int compare = compare(s1, s2, ignoreCase);
    return compare != 0 ? compare : compare(t1, t2, ignoreCase);
  }

  public static int hashCode(@NotNull CharSequence s) {
    return stringHashCode(s);
  }

  public static boolean equals(@Nullable CharSequence s1, @Nullable CharSequence s2) {
    if (s1 == null ^ s2 == null) {
      return false;
    }

    if (s1 == null) {
      return true;
    }

    if (s1.length() != s2.length()) {
      return false;
    }
    for (int i = 0; i < s1.length(); i++) {
      if (s1.charAt(i) != s2.charAt(i)) {
        return false;
      }
    }
    return true;
  }

  public static boolean equalsIgnoreCase(@Nullable CharSequence s1, @Nullable CharSequence s2) {
    if (s1 == null ^ s2 == null) {
      return false;
    }

    if (s1 == null) {
      return true;
    }

    if (s1.length() != s2.length()) {
      return false;
    }
    for (int i = 0; i < s1.length(); i++) {
      if (!charsMatch(s1.charAt(i),s2.charAt(i), true)) {
        return false;
      }
    }
    return true;
  }

  public static boolean charsMatch(char c1, char c2, boolean ignoreCase) {
    // duplicating String.equalsIgnoreCase logic
    if (c1 == c2) {
      return true;
    }
    if (ignoreCase) {
      // If characters don't match but case may be ignored,
      // try converting both characters to uppercase.
      // If the results match, then the comparison scan should
      // continue.
      char u1 = Character.toUpperCase(c1);
      char u2 = Character.toUpperCase(c2);
      if (u1 == u2) {
        return true;
      }
      // Unfortunately, conversion to uppercase does not work properly
      // for the Georgian alphabet, which has strange rules about case
      // conversion.  So we need to make one last check before
      // exiting.
      if (Character.toLowerCase(u1) == Character.toLowerCase(u2)) {
        return true;
      }
    }
    return false;
  }

  public static String formatLinks(String message) {
    Pattern linkPattern = Pattern.compile("http://[a-zA-Z0-9\\./\\-\\+]+");
    StringBuffer result = new StringBuffer();
    Matcher m = linkPattern.matcher(message);
    while (m.find()) {
      m.appendReplacement(result, "<a href=\"" + m.group() + "\">" + m.group() + "</a>");
    }
    m.appendTail(result);
    return result.toString();
  }

  public static boolean isHexDigit(char c) {
    return '0' <= c && c <= '9' || 'a' <= c && c <= 'f' || 'A' <= c && c <= 'F';
  }

  public static boolean isOctalDigit(char c) {
    return '0' <= c && c <= '7';
  }

  @NotNull
  public static String shortenTextWithEllipsis(@NotNull final String text, final int max_length, final int suffix_length) {
    final int prefix_length = max_length - suffix_length - 3;
    assert prefix_length > 0;
    final StringBuilder buffer = new StringBuilder();
    final int textLength = text.length();
    if (textLength > max_length) {
      StringBuilder shorterText = new StringBuilder();
      shorterText.append(text.substring(0, prefix_length));
      shorterText.append("...");
      shorterText.append(text.substring(textLength - suffix_length));
      buffer.append(shorterText.toString());
    }
    else {
      buffer.append(text);
    }
    return buffer.toString();
  }

  public static boolean charsEqualIgnoreCase(char a, char b) {
    return StringUtilRt.charsEqualIgnoreCase(a, b);
  }

  public static char toUpperCase(char a) {
    return StringUtilRt.toUpperCase(a);
  }

  public static char toLowerCase(final char a) {
    return StringUtilRt.toLowerCase(a);
  }

  @NotNull
  public static String convertLineSeparators(@NotNull String text) {
    return StringUtilRt.convertLineSeparators(text);
  }

  @NotNull
  public static String convertLineSeparators(@NotNull String text, boolean keepCarriageReturn) {
    return StringUtilRt.convertLineSeparators(text, keepCarriageReturn);
  }

  @NotNull
  public static String convertLineSeparators(@NotNull String text, @NotNull String newSeparator) {
    return StringUtilRt.convertLineSeparators(text, newSeparator);
  }

  @NotNull
  public static String convertLineSeparators(@NotNull String text, @NotNull String newSeparator, @Nullable int[] offsetsToKeep) {
    return StringUtilRt.convertLineSeparators(text, newSeparator, offsetsToKeep);
  }

  @NotNull
  public static String convertLineSeparators(@NotNull String text,
                                             @NotNull String newSeparator,
                                             @Nullable int[] offsetsToKeep,
                                             boolean keepCarriageReturn) {
    return StringUtilRt.convertLineSeparators(text, newSeparator, offsetsToKeep, keepCarriageReturn);
  }

  public static int parseInt(final String string, final int defaultValue) {
    return StringUtilRt.parseInt(string, defaultValue);
  }

  public static double parseDouble(final String string, final double defaultValue) {
    return StringUtilRt.parseDouble(string, defaultValue);
  }

  @NotNull
  public static String getShortName(@NotNull Class aClass) {
    return StringUtilRt.getShortName(aClass);
  }

  @NotNull
  public static String getShortName(@NotNull String fqName) {
    return StringUtilRt.getShortName(fqName);
  }

  @NotNull
  public static String getShortName(@NotNull String fqName, char separator) {
    return StringUtilRt.getShortName(fqName, separator);
  }
}
