/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.util.*;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.text.CharArrayUtil;
import com.intellij.util.text.CharSequenceSubSequence;
import com.intellij.util.text.StringFactory;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.text.MutableAttributeSet;
import javax.swing.text.html.HTML;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.parser.ParserDelegator;
import java.beans.Introspector;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

//TeamCity inherits StringUtil: do not add private constructors!!!
@SuppressWarnings({"UtilityClassWithoutPrivateConstructor", "MethodOverridesStaticMethodOfSuperclass"})
public class StringUtil extends StringUtilRt {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.util.text.StringUtil");

  @NonNls private static final String VOWELS = "aeiouy";
  @NonNls private static final Pattern EOL_SPLIT_KEEP_SEPARATORS = Pattern.compile("(?<=(\r\n|\n))|(?<=\r)(?=[^\n])");
  @NonNls private static final Pattern EOL_SPLIT_PATTERN = Pattern.compile(" *(\r|\n|\r\n)+ *");
  @NonNls private static final Pattern EOL_SPLIT_PATTERN_WITH_EMPTY = Pattern.compile(" *(\r|\n|\r\n) *");
  @NonNls private static final Pattern EOL_SPLIT_DONT_TRIM_PATTERN = Pattern.compile("(\r|\n|\r\n)+");

  private static class MyHtml2Text extends HTMLEditorKit.ParserCallback {
    @NotNull private final StringBuilder myBuffer = new StringBuilder();

    public void parse(Reader in) throws IOException {
      myBuffer.setLength(0);
      new ParserDelegator().parse(in, this, Boolean.TRUE);
    }

    @Override
    public void handleText(char[] text, int pos) {
      myBuffer.append(text);
    }

    @Override
    public void handleStartTag(HTML.Tag tag, MutableAttributeSet set, int i) {
      handleTag(tag);
    }

    @Override
    public void handleSimpleTag(HTML.Tag tag, MutableAttributeSet set, int i) {
      handleTag(tag);
    }

    private void handleTag(HTML.Tag tag) {
      if (tag.breaksFlow() && myBuffer.length() > 0) {
        myBuffer.append(SystemProperties.getLineSeparator());
      }
    }

    public String getText() {
      return myBuffer.toString();
    }
  }

  private static final MyHtml2Text html2TextParser = new MyHtml2Text();

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
  @Contract(pure = true)
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
  @Contract(pure = true)
  public static String escapePattern(@NotNull final String text) {
    return replace(replace(text, "'", "''"), "{", "'{'");
  }

  @NotNull
  @Contract(pure = true)
  public static <T> Function<T, String> createToStringFunction(@SuppressWarnings("unused") @NotNull Class<T> cls) {
    return new Function<T, String>() {
      @Override
      public String fun(@NotNull T o) {
        return o.toString();
      }
    };
  }

  @NotNull
  public static final Function<String, String> TRIMMER = new Function<String, String>() {
    @Nullable
    @Override
    public String fun(@Nullable String s) {
      return trim(s);
    }
  };

  // Unlike String.replace(CharSequence,CharSequence) does not allocate intermediate objects on non-match
  // TODO revise when JDK9 arrives - its String.replace(CharSequence, CharSequence) is more optimized
  @NotNull
  @Contract(pure = true)
  public static String replace(@NonNls @NotNull String text, @NonNls @NotNull String oldS, @NonNls @NotNull String newS) {
    return replace(text, oldS, newS, false);
  }

  @NotNull
  @Contract(pure = true)
  public static String replaceIgnoreCase(@NonNls @NotNull String text, @NonNls @NotNull String oldS, @NonNls @NotNull String newS) {
    return replace(text, oldS, newS, true);
  }

  /**
   * @deprecated Use {@link String#replace(char,char)} instead
   */
  @NotNull
  @Contract(pure = true)
  @Deprecated
  public static String replaceChar(@NotNull String buffer, char oldChar, char newChar) {
    return buffer.replace(oldChar, newChar);
  }

  @Contract(pure = true)
  public static String replace(@NonNls @NotNull final String text, @NonNls @NotNull final String oldS, @NonNls @NotNull final String newS, final boolean ignoreCase) {
    if (text.length() < oldS.length()) return text;

    StringBuilder newText = null;
    int i = 0;

    while (i < text.length()) {
      final int index = ignoreCase? indexOfIgnoreCase(text, oldS, i) : text.indexOf(oldS, i);
      if (index < 0) {
        if (i == 0) {
          return text;
        }

        newText.append(text, i, text.length());
        break;
      }
      else {
        if (newText == null) {
          if (text.length() == oldS.length()) {
            return newS;
          }
          newText = new StringBuilder(text.length() - i);
        }

        newText.append(text, i, index);
        newText.append(newS);
        i = index + oldS.length();
      }
    }
    return newText != null ? newText.toString() : "";
  }

  /**
   * Implementation copied from {@link String#indexOf(String, int)} except character comparisons made case insensitive
   */
  @Contract(pure = true)
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
  public static boolean containsIgnoreCase(@NotNull String where, @NotNull String what) {
    return indexOfIgnoreCase(where, what, 0) >= 0;
  }

  @Contract(pure = true)
  public static boolean endsWithIgnoreCase(@NonNls @NotNull String str, @NonNls @NotNull String suffix) {
    return StringUtilRt.endsWithIgnoreCase(str, suffix);
  }

  @Contract(pure = true)
  public static boolean startsWithIgnoreCase(@NonNls @NotNull String str, @NonNls @NotNull String prefix) {
    return StringUtilRt.startsWithIgnoreCase(str, prefix);
  }

  @Contract(pure = true)
  @NotNull
  public static String stripHtml(@NotNull String html, boolean convertBreaks) {
    if (convertBreaks) {
      html = html.replaceAll("<br/?>", "\n\n");
    }

    return html.replaceAll("<(.|\n)*?>", "");
  }

  @Contract(value = "null -> null; !null -> !null", pure = true)
  public static String toLowerCase(@Nullable final String str) {
    //noinspection ConstantConditions
    return str == null ? null : str.toLowerCase();
  }

  @NotNull
  @Contract(pure = true)
  public static String getPackageName(@NotNull String fqName) {
    return getPackageName(fqName, '.');
  }

  /**
   * Given a fqName returns the package name for the type or the containing type.
   * <p/>
   * <ul>
   * <li>{@code java.lang.String} -> {@code java.lang}</li>
   * <li>{@code java.util.Map.Entry} -> {@code java.util.Map}</li>
   * </ul>
   *
   * @param fqName    a fully qualified type name. Not supposed to contain any type arguments
   * @param separator the separator to use. Typically '.'
   * @return the package name of the type or the declarator of the type. The empty string if the given fqName is unqualified
   */
  @NotNull
  @Contract(pure = true)
  public static String getPackageName(@NotNull String fqName, char separator) {
    int lastPointIdx = fqName.lastIndexOf(separator);
    if (lastPointIdx >= 0) {
      return fqName.substring(0, lastPointIdx);
    }
    return "";
  }

  @Contract(pure = true)
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

  @Contract(pure = true)
  public static boolean containsLineBreak(@NotNull CharSequence text) {
    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);
      if (isLineBreak(c)) return true;
    }
    return false;
  }

  @Contract(pure = true)
  public static boolean isLineBreak(char c) {
    return c == '\n' || c == '\r';
  }

  @NotNull
  @Contract(pure = true)
  public static String escapeLineBreak(@NotNull String text) {
    StringBuilder buffer = new StringBuilder(text.length());
    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);
      switch (c) {
        case '\n':
          buffer.append("\\n");
          break;
        case '\r':
          buffer.append("\\r");
          break;
        default:
          buffer.append(c);
      }
    }
    return buffer.toString();
  }

  @Contract(pure = true)
  public static boolean endsWithLineBreak(@NotNull CharSequence text) {
    int len = text.length();
    return len > 0 && isLineBreak(text.charAt(len - 1));
  }

  @Contract(pure = true)
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

  @Contract(pure = true)
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
  @Contract(pure = true)
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
  @Contract(pure = true)
  public static String wordsToBeginFromUpperCase(@NotNull String s) {
    return fixCapitalization(s, ourPrepositions, true);
  }

  @NotNull
  @Contract(pure = true)
  public static String wordsToBeginFromLowerCase(@NotNull String s) {
    return fixCapitalization(s, ourPrepositions, false);
  }

  @NotNull
  @Contract(pure = true)
  public static String toTitleCase(@NotNull String s) {
    return fixCapitalization(s, ArrayUtil.EMPTY_STRING_ARRAY, true);
  }

  @NotNull
  private static String fixCapitalization(@NotNull String s, @NotNull String[] prepositions, boolean title) {
    StringBuilder buffer = null;
    for (int i = 0; i < s.length(); i++) {
      char prevChar = i == 0 ? ' ' : s.charAt(i - 1);
      char currChar = s.charAt(i);
      if (!Character.isLetterOrDigit(prevChar) && prevChar != '\'') {
        if (Character.isLetterOrDigit(currChar)) {
          if (title || Character.isUpperCase(currChar)) {
            int j = i;
            for (; j < s.length(); j++) {
              if (!Character.isLetterOrDigit(s.charAt(j))) {
                break;
              }
            }
            if (!title && j > i + 1 && !Character.isLowerCase(s.charAt(i + 1))) {
              // filter out abbreviations like I18n, SQL and CSS
              continue;
            }
            if (!isPreposition(s, i, j - 1, prepositions)) {
              if (buffer == null) {
                buffer = new StringBuilder(s);
              }
              buffer.setCharAt(i, title ? toUpperCase(currChar) : toLowerCase(currChar));
            }
          }
        }
      }
    }
    return buffer == null ? s : buffer.toString();
  }

  @NonNls private static final String[] ourPrepositions = {
    "a", "an", "and", "as", "at", "but", "by", "down", "for", "from", "if", "in", "into", "not", "of", "on", "onto", "or", "out", "over",
    "per", "nor", "the", "to", "up", "upon", "via", "with"
  };

  @Contract(pure = true)
  public static boolean isPreposition(@NotNull String s, int firstChar, int lastChar) {
    return isPreposition(s, firstChar, lastChar, ourPrepositions);
  }

  @Contract(pure = true)
  public static boolean isPreposition(@NotNull String s, int firstChar, int lastChar, @NotNull String[] prepositions) {
    for (String preposition : prepositions) {
      boolean found = false;
      if (lastChar - firstChar + 1 == preposition.length()) {
        found = true;
        for (int j = 0; j < preposition.length(); j++) {
          if (toLowerCase(s.charAt(firstChar + j)) != preposition.charAt(j)) {
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
  @Contract(pure = true)
  public static NotNullFunction<String, String> escaper(final boolean escapeSlash, @Nullable final String additionalChars) {
    return new NotNullFunction<String, String>() {
      @NotNull
      @Override
      public String fun(@NotNull String dom) {
        final StringBuilder builder = new StringBuilder(dom.length());
        escapeStringCharacters(dom.length(), dom, additionalChars, escapeSlash, builder);
        return builder.toString();
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
    return escapeStringCharacters(length, str, additionalChars, escapeSlash, true, buffer);
  }

  @NotNull
  public static StringBuilder escapeStringCharacters(int length,
                                                     @NotNull String str,
                                                     @Nullable String additionalChars,
                                                     boolean escapeSlash,
                                                     boolean escapeUnicode,
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
          else if (escapeUnicode && !isPrintableUnicode(ch)) {
            CharSequence hexCode = StringUtilRt.toUpperCase(Integer.toHexString(ch));
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

  @Contract(pure = true)
  public static boolean isPrintableUnicode(char c) {
    int t = Character.getType(c);
    return t != Character.UNASSIGNED && t != Character.LINE_SEPARATOR && t != Character.PARAGRAPH_SEPARATOR &&
           t != Character.CONTROL && t != Character.FORMAT && t != Character.PRIVATE_USE && t != Character.SURROGATE;
  }

  @NotNull
  @Contract(pure = true)
  public static String escapeStringCharacters(@NotNull String s) {
    StringBuilder buffer = new StringBuilder(s.length());
    escapeStringCharacters(s.length(), s, "\"", buffer);
    return buffer.toString();
  }

  @NotNull
  @Contract(pure = true)
  public static String escapeCharCharacters(@NotNull String s) {
    StringBuilder buffer = new StringBuilder(s.length());
    escapeStringCharacters(s.length(), s, "\'", buffer);
    return buffer.toString();
  }

  @NotNull
  @Contract(pure = true)
  public static String unescapeStringCharacters(@NotNull String s) {
    StringBuilder buffer = new StringBuilder(s.length());
    unescapeStringCharacters(s.length(), s, buffer);
    return buffer.toString();
  }

  private static boolean isQuoteAt(@NotNull String s, int ind) {
    char ch = s.charAt(ind);
    return ch == '\'' || ch == '\"';
  }

  @Contract(pure = true)
  public static boolean isQuotedString(@NotNull String s) {
    return s.length() > 1 && isQuoteAt(s, 0) && s.charAt(0) == s.charAt(s.length() - 1);
  }

  @NotNull
  @Contract(pure = true)
  public static String unquoteString(@NotNull String s) {
    if (isQuotedString(s)) {
      return s.substring(1, s.length() - 1);
    }
    return s;
  }

  @NotNull
  @Contract(pure = true)
  public static String unquoteString(@NotNull String s, char quotationChar) {
    if (s.length() > 1 && quotationChar == s.charAt(0) && quotationChar == s.charAt(s.length() - 1)) {
      return s.substring(1, s.length() - 1);
    }
    return s;
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
        int octalEscapeMaxLength = 2;
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
                int code = Integer.parseInt(s.substring(idx + 1, idx + 5), 16);
                //noinspection AssignmentToForLoopParameter
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

          case '0':
          case '1':
          case '2':
          case '3':
            octalEscapeMaxLength = 3;
          case '4':
          case '5':
          case '6':
          case '7':
            int escapeEnd = idx + 1;
            while (escapeEnd < length && escapeEnd < idx + octalEscapeMaxLength && isOctalDigit(s.charAt(escapeEnd))) escapeEnd++;
            try {
              buffer.append((char)Integer.parseInt(s.substring(idx, escapeEnd), 8));
            }
            catch (NumberFormatException e) {
              throw new RuntimeException("Couldn't parse " + s.substring(idx, escapeEnd), e); // shouldn't happen
            }
            //noinspection AssignmentToForLoopParameter
            idx = escapeEnd - 1;
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

  @NotNull
  @Contract(pure = true)
  public static String pluralize(@NotNull String word) {
    String plural = Pluralizer.PLURALIZER.plural(word);
    if (plural != null) return plural;
    if (word.endsWith("s")) return Pluralizer.restoreCase(word, word + "es");
    return Pluralizer.restoreCase(word, word + "s");
  }

  @NotNull
  @Contract(pure = true)
  public static String capitalizeWords(@NotNull String text,
                                       boolean allWords) {
    return capitalizeWords(text, " \t\n\r\f", allWords, false);
  }

  @NotNull
  @Contract(pure = true)
  public static String capitalizeWords(@NotNull String text,
                                       @NotNull String tokenizerDelim,
                                       boolean allWords,
                                       boolean leaveOriginalDelims) {
    final StringTokenizer tokenizer = new StringTokenizer(text, tokenizerDelim, leaveOriginalDelims);
    final StringBuilder out = new StringBuilder(text.length());
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

  @Contract(pure = true)
  public static String decapitalize(String s) {
    return Introspector.decapitalize(s);
  }

  @Contract(pure = true)
  public static boolean isVowel(char c) {
    return VOWELS.indexOf(c) >= 0;
  }

  /**
   * Capitalize the first letter of the sentence.
   */
  @NotNull
  @Contract(pure = true)
  public static String capitalize(@NotNull String s) {
    if (s.isEmpty()) return s;
    if (s.length() == 1) return StringUtilRt.toUpperCase(s).toString();

    // Optimization
    if (Character.isUpperCase(s.charAt(0))) return s;
    return toUpperCase(s.charAt(0)) + s.substring(1);
  }

  @Contract(value = "null -> false", pure = true)
  public static boolean isCapitalized(@Nullable String s) {
    return s != null && !s.isEmpty() && Character.isUpperCase(s.charAt(0));
  }

  @NotNull
  @Contract(pure = true)
  public static String capitalizeWithJavaBeanConvention(@NotNull String s) {
    if (s.length() > 1 && Character.isUpperCase(s.charAt(1))) {
      return s;
    }
    return capitalize(s);
  }

  @Contract(pure = true)
  public static int stringHashCode(@NotNull CharSequence chars) {
    if (chars instanceof String || chars instanceof CharSequenceWithStringHash) {
      // we know for sure these classes have conformant (and maybe faster) hashCode()
      return chars.hashCode();
    }

    return stringHashCode(chars, 0, chars.length());
  }

  @Contract(pure = true)
  public static int stringHashCode(@NotNull CharSequence chars, int from, int to) {
    int h = 0;
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
  public static int stringHashCodeInsensitive(@NotNull char[] chars, int from, int to) {
    int h = 0;
    for (int off = from; off < to; off++) {
      h = 31 * h + toLowerCase(chars[off]);
    }
    return h;
  }

  @Contract(pure = true)
  public static int stringHashCodeInsensitive(@NotNull CharSequence chars, int from, int to) {
    int h = 0;
    for (int off = from; off < to; off++) {
      h = 31 * h + toLowerCase(chars.charAt(off));
    }
    return h;
  }

  @Contract(pure = true)
  public static int stringHashCodeInsensitive(@NotNull CharSequence chars) {
    return stringHashCodeInsensitive(chars, 0, chars.length());
  }

  @Contract(pure = true)
  public static int stringHashCodeIgnoreWhitespaces(@NotNull char[] chars, int from, int to) {
    int h = 0;
    for (int off = from; off < to; off++) {
      char c = chars[off];
      if (!isWhiteSpace(c)) {
        h = 31 * h + c;
      }
    }
    return h;
  }

  @Contract(pure = true)
  public static int stringHashCodeIgnoreWhitespaces(@NotNull CharSequence chars, int from, int to) {
    int h = 0;
    for (int off = from; off < to; off++) {
      char c = chars.charAt(off);
      if (!isWhiteSpace(c)) {
        h = 31 * h + c;
      }
    }
    return h;
  }

  @Contract(pure = true)
  public static int stringHashCodeIgnoreWhitespaces(@NotNull CharSequence chars) {
    return stringHashCodeIgnoreWhitespaces(chars, 0, chars.length());
  }

  /**
   * Equivalent to string.startsWith(prefixes[0] + prefixes[1] + ...) but avoids creating an object for concatenation.
   */
  @Contract(pure = true)
  public static boolean startsWithConcatenation(@NotNull String string, @NotNull String... prefixes) {
    int offset = 0;
    for (String prefix : prefixes) {
      int prefixLen = prefix.length();
      if (!string.regionMatches(offset, prefix, 0, prefixLen)) {
        return false;
      }
      offset += prefixLen;
    }
    return true;
  }

  @Contract(value = "null -> null; !null -> !null", pure = true)
  public static String trim(@Nullable String s) {
    return s == null ? null : s.trim();
  }

  @NotNull
  @Contract(pure = true)
  public static String trimEnd(@NotNull String s, @NonNls @NotNull String suffix) {
    return trimEnd(s, suffix, false);
  }

  @NotNull
  @Contract(pure = true)
  public static String trimEnd(@NotNull String s, @NonNls @NotNull String suffix, boolean ignoreCase) {
    boolean endsWith = ignoreCase ? endsWithIgnoreCase(s, suffix) : s.endsWith(suffix);
    if (endsWith) {
      return s.substring(0, s.length() - suffix.length());
    }
    return s;
  }

  @NotNull
  @Contract(pure = true)
  public static String trimEnd(@NotNull String s, char suffix) {
    if (endsWithChar(s, suffix)) {
      return s.substring(0, s.length() - 1);
    }
    return s;
  }

  @NotNull
  @Contract(pure = true)
  public static String trimLog(@NotNull final String text, final int limit) {
    if (limit > 5 && text.length() > limit) {
      return text.substring(0, limit - 5) + " ...\n";
    }
    return text;
  }

  @NotNull
  @Contract(pure = true)
  public static String trimLeading(@NotNull String string) {
    return trimLeading((CharSequence)string).toString();
  }
  @NotNull
  @Contract(pure = true)
  public static CharSequence trimLeading(@NotNull CharSequence string) {
    int index = 0;
    while (index < string.length() && Character.isWhitespace(string.charAt(index))) index++;
    return string.subSequence(index, string.length());
  }

  @NotNull
  @Contract(pure = true)
  public static String trimLeading(@NotNull String string, char symbol) {
    int index = 0;
    while (index < string.length() && string.charAt(index) == symbol) index++;
    return string.substring(index);
  }

  @NotNull
  public static StringBuilder trimLeading(@NotNull StringBuilder builder, char symbol) {
    int index = 0;
    while (index < builder.length() && builder.charAt(index) == symbol) index++;
    if (index > 0) builder.delete(0, index);
    return builder;
  }

  @NotNull
  @Contract(pure = true)
  public static String trimTrailing(@NotNull String string) {
    return trimTrailing((CharSequence)string).toString();
  }

  @NotNull
  @Contract(pure = true)
  public static CharSequence trimTrailing(@NotNull CharSequence string) {
    int index = string.length() - 1;
    while (index >= 0 && Character.isWhitespace(string.charAt(index))) index--;
    return string.subSequence(0, index + 1);
  }

  @NotNull
  @Contract(pure = true)
  public static String trimTrailing(@NotNull String string, char symbol) {
    int index = string.length() - 1;
    while (index >= 0 && string.charAt(index) == symbol) index--;
    return string.substring(0, index + 1);
  }

  @NotNull
  public static StringBuilder trimTrailing(@NotNull StringBuilder builder, char symbol) {
    int index = builder.length() - 1;
    while (index >= 0 && builder.charAt(index) == symbol) index--;
    builder.setLength(index + 1);
    return builder;
  }

  @Contract(pure = true)
  public static boolean startsWithChar(@Nullable CharSequence s, char prefix) {
    return s != null && s.length() != 0 && s.charAt(0) == prefix;
  }

  @Contract(pure = true)
  public static boolean endsWithChar(@Nullable CharSequence s, char suffix) {
    return StringUtilRt.endsWithChar(s, suffix);
  }

  @NotNull
  @Contract(pure = true)
  public static String trimStart(@NotNull String s, @NonNls @NotNull String prefix) {
    if (s.startsWith(prefix)) {
      return s.substring(prefix.length());
    }
    return s;
  }

  @NotNull
  @Contract(pure = true)
  public static String trimExtensions(@NotNull String name) {
    int index = name.indexOf('.');
    return index < 0 ? name : name.substring(0, index);
  }

  @NotNull
  @Contract(pure = true)
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

  @Contract(pure = true)
  public static String defaultIfEmpty(@Nullable String value, String defaultValue) {
    return isEmpty(value) ? defaultValue : value;
  }

  @Contract(value = "null -> false", pure = true)
  public static boolean isNotEmpty(@Nullable String s) {
    return !isEmpty(s);
  }

  @Contract(value = "null -> true", pure = true)
  public static boolean isEmpty(@Nullable String s) {
    return s == null || s.isEmpty();
  }

  @Contract(value = "null -> true",pure = true)
  public static boolean isEmpty(@Nullable CharSequence cs) {
    return cs == null || cs.length() == 0;
  }

  @Contract(pure = true)
  public static int length(@Nullable CharSequence cs) {
    return cs == null ? 0 : cs.length();
  }

  @NotNull
  @Contract(pure = true)
  public static String notNullize(@Nullable final String s) {
    return notNullize(s, "");
  }

  @NotNull
  @Contract(pure = true)
  public static String notNullize(@Nullable final String s, @NotNull String defaultValue) {
    return s == null ? defaultValue : s;
  }

  @Nullable
  @Contract(pure = true)
  public static String nullize(@Nullable final String s) {
    return nullize(s, false);
  }

  @Nullable
  @Contract(pure = true)
  public static String nullize(@Nullable final String s, boolean nullizeSpaces) {
    if (nullizeSpaces) {
      if (isEmptyOrSpaces(s)) return null;
    }
    else {
      if (isEmpty(s)) return null;
    }
    return s;
  }

  @Contract(value = "null -> true",pure = true)
  // we need to keep this method to preserve backward compatibility
  public static boolean isEmptyOrSpaces(@Nullable String s) {
    return isEmptyOrSpaces((CharSequence)s);
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

  /**
   * Allows to answer if given symbol is white space, tabulation or line feed.
   *
   * @param c symbol to check
   * @return {@code true} if given symbol is white space, tabulation or line feed; {@code false} otherwise
   */
  @Contract(pure = true)
  public static boolean isWhiteSpace(char c) {
    return c == '\n' || c == '\t' || c == ' ';
  }

  @NotNull
  @Contract(pure = true)
  public static String getThrowableText(@NotNull Throwable aThrowable) {
    return ExceptionUtil.getThrowableText(aThrowable);
  }

  @NotNull
  @Contract(pure = true)
  public static String getThrowableText(@NotNull Throwable aThrowable, @NonNls @NotNull final String stackFrameSkipPattern) {
    return ExceptionUtil.getThrowableText(aThrowable, stackFrameSkipPattern);
  }

  @Nullable
  @Contract(pure = true)
  public static String getMessage(@NotNull Throwable e) {
    return ExceptionUtil.getMessage(e);
  }

  @NotNull
  @Contract(pure = true)
  public static String repeatSymbol(final char aChar, final int count) {
    char[] buffer = new char[count];
    Arrays.fill(buffer, aChar);
    return StringFactory.createShared(buffer);
  }

  @NotNull
  @Contract(pure = true)
  public static String repeat(@NotNull String s, int count) {
    assert count >= 0 : count;
    StringBuilder sb = new StringBuilder(s.length() * count);
    for (int i = 0; i < count; i++) {
      sb.append(s);
    }
    return sb.toString();
  }

  @NotNull
  @Contract(pure = true)
  public static List<String> splitHonorQuotes(@NotNull String s, char separator) {
    final List<String> result = new ArrayList<String>();
    final StringBuilder builder = new StringBuilder(s.length());
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
  @Contract(pure = true)
  public static List<String> split(@NotNull String s, @NotNull String separator) {
    return split(s, separator, true);
  }
  @NotNull
  @Contract(pure = true)
  public static List<CharSequence> split(@NotNull CharSequence s, @NotNull CharSequence separator) {
    return split(s, separator, true, true);
  }

  @NotNull
  @Contract(pure = true)
  public static List<String> split(@NotNull String s, @NotNull String separator,
                                   boolean excludeSeparator) {
    return split(s, separator, excludeSeparator, true);
  }

  @NotNull
  @Contract(pure = true)
  public static List<String> split(@NotNull String s, @NotNull String separator,
                                   boolean excludeSeparator, boolean excludeEmptyStrings) {
    return (List)split((CharSequence)s,separator,excludeSeparator,excludeEmptyStrings);
  }
  @NotNull
  @Contract(pure = true)
  public static List<CharSequence> split(@NotNull CharSequence s, @NotNull CharSequence separator,
                                   boolean excludeSeparator, boolean excludeEmptyStrings) {
    if (separator.length() == 0) {
      return Collections.singletonList(s);
    }
    List<CharSequence> result = new ArrayList<CharSequence>();
    int pos = 0;
    while (true) {
      int index = indexOf(s,separator, pos);
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

  @NotNull
  @Contract(pure = true)
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
  @Contract(pure = true)
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

  /**
   * @return list containing all words in {@code text}, or {@link ContainerUtil#emptyList()} if there are none.
   * The <b>word</b> here means the maximum sub-string consisting entirely of characters which are {@code Character.isJavaIdentifierPart(c)}.
   */
  @NotNull
  @Contract(pure = true)
  public static List<String> getWordsIn(@NotNull String text) {
    List<String> result = null;
    int start = -1;
    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);
      boolean isIdentifierPart = Character.isJavaIdentifierPart(c);
      if (isIdentifierPart && start == -1) {
        start = i;
      }
      if (isIdentifierPart && i == text.length() - 1 && start != -1) {
        if (result == null) {
          result = new SmartList<String>();
        }
        result.add(text.substring(start, i + 1));
      }
      else if (!isIdentifierPart && start != -1) {
        if (result == null) {
          result = new SmartList<String>();
        }
        result.add(text.substring(start, i));
        start = -1;
      }
    }
    if (result == null) {
      return ContainerUtil.emptyList();
    }
    return result;
  }

  @NotNull
  @Contract(pure = true)
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
  @Contract(pure = true)
  public static String join(@NotNull final String[] strings, @NotNull final String separator) {
    return join(strings, 0, strings.length, separator);
  }

  @NotNull
  @Contract(pure = true)
  public static String join(@NotNull final String[] strings, int startIndex, int endIndex, @NotNull final String separator) {
    final StringBuilder result = new StringBuilder();
    for (int i = startIndex; i < endIndex; i++) {
      if (i > startIndex) result.append(separator);
      result.append(strings[i]);
    }
    return result.toString();
  }

  @NotNull
  @Contract(pure = true)
  public static String[] zip(@NotNull String[] strings1, @NotNull String[] strings2, String separator) {
    if (strings1.length != strings2.length) throw new IllegalArgumentException();

    String[] result = ArrayUtil.newStringArray(strings1.length);
    for (int i = 0; i < result.length; i++) {
      result[i] = strings1[i] + separator + strings2[i];
    }

    return result;
  }

  @NotNull
  @Contract(pure = true)
  public static String[] surround(@NotNull String[] strings1, String prefix, String suffix) {
    String[] result = ArrayUtil.newStringArray(strings1.length);
    for (int i = 0; i < result.length; i++) {
      result[i] = prefix + strings1[i] + suffix;
    }

    return result;
  }

  @NotNull
  @Contract(pure = true)
  public static <T> String join(@NotNull T[] items, @NotNull Function<T, String> f, @NotNull @NonNls String separator) {
    return join(Arrays.asList(items), f, separator);
  }

  @NotNull
  @Contract(pure = true)
  public static <T> String join(@NotNull Collection<? extends T> items,
                                @NotNull Function<? super T, String> f,
                                @NotNull @NonNls String separator) {
    if (items.isEmpty()) return "";
    if (items.size() == 1) return notNullize(f.fun(items.iterator().next()));
    return join((Iterable<? extends T>)items, f, separator);
  }

  @Contract(pure = true)
  public static String join(@NotNull Iterable<?> items, @NotNull @NonNls String separator) {
    StringBuilder result = new StringBuilder();
    for (Object item : items) {
      result.append(item).append(separator);
    }
    if (result.length() > 0) {
      result.setLength(result.length() - separator.length());
    }
    return result.toString();
  }

  @NotNull
  @Contract(pure = true)
  public static <T> String join(@NotNull Iterable<? extends T> items,
                                @NotNull Function<? super T, String> f,
                                @NotNull @NonNls String separator) {
    final StringBuilder result = new StringBuilder();
    join(items, f, separator, result);
    return result.toString();
  }

  public static <T> void join(@NotNull Iterable<? extends T> items,
                              @NotNull Function<? super T, String> f,
                              @NotNull @NonNls String separator,
                              @NotNull StringBuilder result) {
    boolean isFirst = true;
    for (T item : items) {
      String string = f.fun(item);
      if (string != null && !string.isEmpty()) {
        if (isFirst) {
          isFirst = false;
        } else {
          result.append(separator);
        }
        result.append(string);
      }
    }
  }

  @NotNull
  @Contract(pure = true)
  public static String join(@NotNull Collection<String> strings, @NotNull String separator) {
    if (strings.size() <= 1) {
      return notNullize(ContainerUtil.getFirstItem(strings));
    }
    StringBuilder result = new StringBuilder();
    join(strings, separator, result);
    return result.toString();
  }

  public static void join(@NotNull Collection<String> strings, @NotNull String separator, @NotNull StringBuilder result) {
    boolean isFirst = true;
    for (String string : strings) {
      if (string != null) {
        if (isFirst) {
          isFirst = false;
        }
        else {
          result.append(separator);
        }
        result.append(string);
      }
    }
  }

  @NotNull
  @Contract(pure = true)
  public static String join(@NotNull final int[] strings, @NotNull final String separator) {
    final StringBuilder result = new StringBuilder();
    for (int i = 0; i < strings.length; i++) {
      if (i > 0) result.append(separator);
      result.append(strings[i]);
    }
    return result.toString();
  }

  @NotNull
  @Contract(pure = true)
  public static String join(@Nullable final String... strings) {
    if (strings == null || strings.length == 0) return "";

    final StringBuilder builder = new StringBuilder();
    for (final String string : strings) {
      builder.append(string);
    }
    return builder.toString();
  }

  /**
   * Consider using {@link StringUtil#unquoteString(String)} instead.
   * Note: this method has an odd behavior:
   *   Quotes are removed even if leading and trailing quotes are different or
   *                           if there is only one quote (leading or trailing).
   */
  @NotNull
  @Contract(pure = true)
  public static String stripQuotesAroundValue(@NotNull String text) {
    final int len = text.length();
    if (len > 0) {
      final int from = isQuoteAt(text, 0) ? 1 : 0;
      final int to = len > 1 && isQuoteAt(text, len - 1) ? len - 1 : len;
      if (from > 0 || to < len) {
        return text.substring(from, to);
      }
    }
    return text;
  }

  /**
   * Formats the specified file size as a string.
   *
   * @param fileSize the size to format.
   * @return the size formatted as a string.
   * @since 5.0.1
   */
  @NotNull
  @Contract(pure = true)
  public static String formatFileSize(long fileSize) {
    return formatValue(fileSize, null,
                       new String[]{"B", "K", "M", "G", "T", "P", "E"},
                       new long[]{1000, 1000, 1000, 1000, 1000, 1000});
  }

  @NotNull
  @Contract(pure = true)
  public static String formatDuration(long duration) {
    return formatValue(duration, " ",
                       new String[]{"ms", "s", "m", "h", "d", "w", "mo", "yr", "c", "ml", "ep"},
                       new long[]{1000, 60, 60, 24, 7, 4, 12, 100, 10, 10000});
  }

  @NotNull
  private static String formatValue(long value, String partSeparator, String[] units, long[] multipliers) {
    StringBuilder sb = new StringBuilder();
    long count = value;
    long remainder = 0;
    int i = 0;
    for (; i < units.length; i++) {
      long multiplier = i < multipliers.length ? multipliers[i] : -1;
      if (multiplier == -1 || count < multiplier) break;
      remainder = count % multiplier;
      count /= multiplier;
      if (partSeparator != null && (remainder != 0 || sb.length() > 0)) {
        sb.insert(0, units[i]).insert(0, remainder).insert(0, partSeparator);
      }
    }
    if (partSeparator != null || remainder == 0) {
      sb.insert(0, units[i]).insert(0, count);
    }
    else if (remainder > 0) {
      sb.append(String.format(Locale.US, "%.2f", count + (double)remainder / multipliers[i - 1])).append(units[i]);
    }
    return sb.toString();
  }

  /**
   * Returns unpluralized variant using English based heuristics like properties -> property, names -> name, children -> child.
   * Returns {@code null} if failed to match appropriate heuristic.
   *
   * @param word english word in plural form
   * @return name in singular form or {@code null} if failed to find one.
   */
  @Nullable
  @Contract(pure = true)
  public static String unpluralize(@NotNull String word) {
    String singular = Pluralizer.PLURALIZER.singular(word);
    if (singular != null) return singular;
    if (word.endsWith("es")) return nullize(trimEnd(word, "es", true));
    if (word.endsWith("s")) return nullize(trimEnd(word, "s", true));
    return null;
  }

  @Contract(pure = true)
  public static boolean containsAlphaCharacters(@NotNull String value) {
    for (int i = 0; i < value.length(); i++) {
      if (Character.isLetter(value.charAt(i))) return true;
    }
    return false;
  }

  @Contract(pure = true)
  public static boolean containsAnyChar(@NotNull final String value, @NotNull final String chars) {
    return chars.length() > value.length()
           ? containsAnyChar(value, chars, 0, value.length())
           : containsAnyChar(chars, value, 0, chars.length());
  }

  @Contract(pure = true)
  public static boolean containsAnyChar(@NotNull final String value,
                                        @NotNull final String chars,
                                        final int start, final int end) {
    for (int i = start; i < end; i++) {
      if (chars.indexOf(value.charAt(i)) >= 0) {
        return true;
      }
    }

    return false;
  }

  @Contract(pure = true)
  public static boolean containsChar(@NotNull final String value, final char ch) {
    return value.indexOf(ch) >= 0;
  }

  /**
   * @deprecated use #capitalize(String)
   */
  @Contract(value = "null -> null; !null -> !null", pure = true)
  public static String firstLetterToUpperCase(@Nullable final String displayString) {
    if (displayString == null || displayString.isEmpty()) return displayString;
    char firstChar = displayString.charAt(0);
    char uppedFirstChar = toUpperCase(firstChar);

    if (uppedFirstChar == firstChar) return displayString;

    char[] buffer = displayString.toCharArray();
    buffer[0] = uppedFirstChar;
    return StringFactory.createShared(buffer);
  }

  /**
   * Strip out all characters not accepted by given filter
   *
   * @param s      e.g. "/n    my string "
   * @param filter e.g. {@link CharFilter#NOT_WHITESPACE_FILTER}
   * @return stripped string e.g. "mystring"
   */
  @NotNull
  @Contract(pure = true)
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
  @Contract(pure = true)
  public static List<String> findMatches(@NotNull String s, @NotNull Pattern pattern) {
    return findMatches(s, pattern, 1);
  }

  @NotNull
  @Contract(pure = true)
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
  @Contract(pure = true)
  public static int findFirst(@NotNull final CharSequence s, @NotNull CharFilter filter) {
    for (int i = 0; i < s.length(); i++) {
      char ch = s.charAt(i);
      if (filter.accept(ch)) {
        return i;
      }
    }
    return -1;
  }

  @NotNull
  @Contract(pure = true)
  public static String replaceSubstring(@NotNull String string, @NotNull TextRange range, @NotNull String replacement) {
    return range.replace(string, replacement);
  }

  @Contract(pure = true)
  public static boolean startsWithWhitespace(@NotNull String text) {
    return !text.isEmpty() && Character.isWhitespace(text.charAt(0));
  }

  @Contract(pure = true)
  public static boolean isChar(CharSequence seq, int index, char c) {
    return index >= 0 && index < seq.length() && seq.charAt(index) == c;
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
  public static boolean startsWith(@NotNull CharSequence text, int startIndex, @NotNull CharSequence prefix) {
    int l1 = text.length() - startIndex;
    int l2 = prefix.length();
    if (l1 < l2) return false;

    for (int i = 0; i < l2; i++) {
      if (text.charAt(i + startIndex) != prefix.charAt(i)) return false;
    }

    return true;
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

  @NotNull
  @Contract(pure = true)
  public static String commonPrefix(@NotNull String s1, @NotNull String s2) {
    return s1.substring(0, commonPrefixLength(s1, s2));
  }

  @Contract(pure = true)
  public static int commonPrefixLength(@NotNull CharSequence s1, @NotNull CharSequence s2) {
    return commonPrefixLength(s1, s2, false);
  }

  @Contract(pure = true)
  public static int commonPrefixLength(@NotNull CharSequence s1, @NotNull CharSequence s2, boolean ignoreCase) {
    int i;
    int minLength = Math.min(s1.length(), s2.length());
    for (i = 0; i < minLength; i++) {
      if (!charsMatch(s1.charAt(i), s2.charAt(i), ignoreCase)) {
        break;
      }
    }
    return i;
  }

  @NotNull
  @Contract(pure = true)
  public static String commonSuffix(@NotNull String s1, @NotNull String s2) {
    return s1.substring(s1.length() - commonSuffixLength(s1, s2));
  }

  @Contract(pure = true)
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
  public static boolean containsWhitespaces(@Nullable CharSequence s) {
    if (s == null) return false;

    for (int i = 0; i < s.length(); i++) {
      if (Character.isWhitespace(s.charAt(i))) return true;
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
  public static boolean contains(@NotNull CharSequence sequence, @NotNull CharSequence infix) {
    return indexOf(sequence, infix) >= 0;
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
  public static int indexOf(@NotNull char[] s, char c, int start, int end, boolean caseSensitive) {
    end = Math.min(end, s.length);
    for (int i = Math.max(start, 0); i < end; i++) {
      if (charsMatch(s[i], c, !caseSensitive)) return i;
    }
    return -1;
  }

  @Contract(pure = true)
  public static int indexOfSubstringEnd(@NotNull String text, @NotNull String subString) {
    int i = text.indexOf(subString);
    if (i == -1) return -1;
    return i + subString.length();
  }

  @Contract(pure = true)
  public static int indexOfAny(@NotNull final String s, @NotNull final String chars) {
    return indexOfAny(s, chars, 0, s.length());
  }

  @Contract(pure = true)
  public static int indexOfAny(@NotNull final CharSequence s, @NotNull final String chars) {
    return indexOfAny(s, chars, 0, s.length());
  }

  @Contract(pure = true)
  public static int indexOfAny(@NotNull final String s, @NotNull final String chars, final int start, final int end) {
    return indexOfAny((CharSequence)s, chars, start, end);
  }

  @Contract(pure = true)
  public static int indexOfAny(@NotNull final CharSequence s, @NotNull final String chars, final int start, int end) {
    end = Math.min(end, s.length());
    for (int i = Math.max(start, 0); i < end; i++) {
      if (containsChar(chars, s.charAt(i))) return i;
    }
    return -1;
  }

  @Contract(pure = true)
  public static int lastIndexOfAny(@NotNull CharSequence s, @NotNull final String chars) {
    for (int i = s.length() - 1; i >= 0; i--) {
      if (containsChar(chars, s.charAt(i))) return i;
    }
    return -1;
  }

  @Nullable
  @Contract(pure = true)
  public static String substringBefore(@NotNull String text, @NotNull String subString) {
    int i = text.indexOf(subString);
    if (i == -1) return null;
    return text.substring(0, i);
  }

  @Nullable
  @Contract(pure = true)
  public static String substringAfter(@NotNull String text, @NotNull String subString) {
    int i = text.indexOf(subString);
    if (i == -1) return null;
    return text.substring(i + subString.length());
  }

  @Nullable
  @Contract(pure = true)
  public static String substringAfterLast(@NotNull String text, @NotNull String subString) {
    int i = text.lastIndexOf(subString);
    if (i == -1) return null;
    return text.substring(i + subString.length());
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
    return StringUtilRt.lastIndexOf(s, c, start, end);
  }

  @NotNull
  @Contract(pure = true)
  public static String first(@NotNull String text, final int maxLength, final boolean appendEllipsis) {
    return text.length() > maxLength ? text.substring(0, maxLength) + (appendEllipsis ? "..." : "") : text;
  }

  @NotNull
  @Contract(pure = true)
  public static CharSequence first(@NotNull CharSequence text, final int length, final boolean appendEllipsis) {
    return text.length() > length ? text.subSequence(0, length) + (appendEllipsis ? "..." : "") : text;
  }

  @NotNull
  @Contract(pure = true)
  public static CharSequence last(@NotNull CharSequence text, final int length, boolean prependEllipsis) {
    return text.length() > length ? (prependEllipsis ? "..." : "") + text.subSequence(text.length() - length, text.length()) : text;
  }

  @NotNull
  @Contract(pure = true)
  public static String firstLast(@NotNull String text, int length) {
    return text.length() > length
           ? text.subSequence(0, length / 2) + "\u2026" + text.subSequence(text.length() - length / 2 - 1, text.length())
           : text;
  }

  @NotNull
  @Contract(pure = true)
  public static String escapeChar(@NotNull final String str, final char character) {
    return escapeChars(str, character);
  }

  @NotNull
  @Contract(pure = true)
  public static String escapeChars(@NotNull final String str, final char... character) {
    final StringBuilder buf = new StringBuilder(str);
    for (char c : character) {
      escapeChar(buf, c);
    }
    return buf.toString();
  }

  public static void escapeChar(@NotNull final StringBuilder buf, final char character) {
    int idx = 0;
    while ((idx = indexOf(buf, character, idx)) >= 0) {
      buf.insert(idx, "\\");
      idx += 2;
    }
  }

  @NotNull
  @Contract(pure = true)
  public static String escapeQuotes(@NotNull final String str) {
    return escapeChar(str, '"');
  }

  public static void escapeQuotes(@NotNull final StringBuilder buf) {
    escapeChar(buf, '"');
  }

  @NotNull
  @Contract(pure = true)
  public static String escapeSlashes(@NotNull final String str) {
    return escapeChar(str, '/');
  }

  @NotNull
  @Contract(pure = true)
  public static String escapeBackSlashes(@NotNull final String str) {
    return escapeChar(str, '\\');
  }

  public static void escapeSlashes(@NotNull final StringBuilder buf) {
    escapeChar(buf, '/');
  }

  @NotNull
  @Contract(pure = true)
  public static String unescapeSlashes(@NotNull final String str) {
    final StringBuilder buf = new StringBuilder(str.length());
    unescapeChar(buf, str, '/');
    return buf.toString();
  }

  @NotNull
  @Contract(pure = true)
  public static String unescapeBackSlashes(@NotNull final String str) {
    final StringBuilder buf = new StringBuilder(str.length());
    unescapeChar(buf, str, '\\');
    return buf.toString();
  }

  @NotNull
  @Contract(pure = true)
  public static String unescapeChar(@NotNull final String str, char unescapeChar) {
    final StringBuilder buf = new StringBuilder(str.length());
    unescapeChar(buf, str, unescapeChar);
    return buf.toString();
  }

  private static void unescapeChar(@NotNull StringBuilder buf, @NotNull String str, char unescapeChar) {
    final int length = str.length();
    final int last = length - 1;
    for (int i = 0; i < length; i++) {
      char ch = str.charAt(i);
      if (ch == '\\' && i != last) {
        i++;
        ch = str.charAt(i);
        if (ch != unescapeChar) buf.append('\\');
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

  @NotNull
  @Contract(pure = true)
  public static String wrapWithDoubleQuote(@NotNull String str) {
    return '\"' + str + "\"";
  }

  @NonNls private static final String[] REPLACES_REFS = {"&lt;", "&gt;", "&amp;", "&#39;", "&quot;"};
  @NonNls private static final String[] REPLACES_DISP = {"<", ">", "&", "'", "\""};

  @Contract(value = "null -> null; !null -> !null",pure = true)
  public static String unescapeXml(@Nullable final String text) {
    if (text == null) return null;
    return replace(text, REPLACES_REFS, REPLACES_DISP);
  }

  @Contract(value = "null -> null; !null -> !null",pure = true)
  public static String escapeXml(@Nullable final String text) {
    if (text == null) return null;
    return replace(text, REPLACES_DISP, REPLACES_REFS);
  }

  public static String removeHtmlTags (@Nullable String htmlString) {
    if (isEmpty(htmlString)) return htmlString;
    try {
      html2TextParser.parse(new StringReader(htmlString));
    }
    catch (IOException e) {
        LOG.error(e);
    }
    return html2TextParser.getText();
  }

  @NonNls private static final String[] MN_QUOTED = {"&&", "__"};
  @NonNls private static final String[] MN_CHARS = {"&", "_"};

  @Contract(value = "null -> null; !null -> !null", pure = true)
  public static String escapeMnemonics(@Nullable String text) {
    if (text == null) return null;
    return replace(text, MN_CHARS, MN_QUOTED);
  }

  @NotNull
  @Contract(pure = true)
  public static String htmlEmphasize(@NotNull String text) {
    return "<b><code>" + escapeXml(text) + "</code></b>";
  }


  @NotNull
  @Contract(pure = true)
  public static String escapeToRegexp(@NotNull String text) {
    final StringBuilder result = new StringBuilder(text.length());
    return escapeToRegexp(text, result).toString();
  }

  @NotNull
  public static StringBuilder escapeToRegexp(@NotNull CharSequence text, @NotNull StringBuilder builder) {
    for (int i = 0; i < text.length(); i++) {
      final char c = text.charAt(i);
      if (c == ' ' || Character.isLetter(c) || Character.isDigit(c) || c == '_') {
        builder.append(c);
      }
      else if (c == '\n') {
        builder.append("\\n");
      }
      else if (c == '\r') {
        builder.append("\\r");
      }
      else {
        builder.append('\\').append(c);
      }
    }

    return builder;
  }

  @Contract(pure = true)
  public static boolean isEscapedBackslash(@NotNull char[] chars, int startOffset, int backslashOffset) {
    if (chars[backslashOffset] != '\\') {
      return true;
    }
    boolean escaped = false;
    for (int i = startOffset; i < backslashOffset; i++) {
      if (chars[i] == '\\') {
        escaped = !escaped;
      }
      else {
        escaped = false;
      }
    }
    return escaped;
  }

  @Contract(pure = true)
  public static boolean isEscapedBackslash(@NotNull CharSequence text, int startOffset, int backslashOffset) {
    if (text.charAt(backslashOffset) != '\\') {
      return true;
    }
    boolean escaped = false;
    for (int i = startOffset; i < backslashOffset; i++) {
      if (text.charAt(i) == '\\') {
        escaped = !escaped;
      }
      else {
        escaped = false;
      }
    }
    return escaped;
  }

  @NotNull
  @Contract(pure = true)
  public static String replace(@NotNull String text, @NotNull String[] from, @NotNull String[] to) {
    return replace(text, Arrays.asList(from), Arrays.asList(to));
  }

  @NotNull
  @Contract(pure = true)
  public static String replace(@NotNull String text, @NotNull List<String> from, @NotNull List<String> to) {
    assert from.size() == to.size();
    final StringBuilder result = new StringBuilder(text.length());
    replace:
    for (int i = 0; i < text.length(); i++) {
      for (int j = 0; j < from.size(); j += 1) {
        String toReplace = from.get(j);
        String replaceWith = to.get(j);

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
  @Contract(pure = true)
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

  @Contract(pure = true)
  public static int countNewLines(@NotNull CharSequence text) {
    return countChars(text, '\n');
  }

  @Contract(pure = true)
  public static int countChars(@NotNull CharSequence text, char c) {
    return countChars(text, c, 0, false);
  }

  @Contract(pure = true)
  public static int countChars(@NotNull CharSequence text, char c, int offset, boolean stopAtOtherChar) {
    return countChars(text, c, offset, text.length(), stopAtOtherChar);
  }

  @Contract(pure = true)
  public static int countChars(@NotNull CharSequence text, char c, int start, int end, boolean stopAtOtherChar) {
    int count = 0;
    for (int i = start, len = Math.min(text.length(), end); i < len; ++i) {
      if (text.charAt(i) == c) {
        count++;
      }
      else if (stopAtOtherChar) {
        break;
      }
    }
    return count;
  }

  @NotNull
  @Contract(pure = true)
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
  @Contract(pure = true)
  public static String joinOrNull(@NotNull String... args) {
    StringBuilder r = new StringBuilder();
    for (String arg : args) {
      if (arg == null) return null;
      r.append(arg);
    }
    return r.toString();
  }

  @Nullable
  @Contract(pure = true)
  public static String getPropertyName(@NonNls @NotNull String methodName) {
    if (methodName.startsWith("get")) {
      return Introspector.decapitalize(methodName.substring(3));
    }
    if (methodName.startsWith("is")) {
      return Introspector.decapitalize(methodName.substring(2));
    }
    if (methodName.startsWith("set")) {
      return Introspector.decapitalize(methodName.substring(3));
    }
    return null;
  }

  @Contract(pure = true)
  public static boolean isJavaIdentifierStart(char c) {
    return c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z' || Character.isJavaIdentifierStart(c);
  }

  @Contract(pure = true)
  public static boolean isJavaIdentifierPart(char c) {
    return c >= '0' && c <= '9' || c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z' || Character.isJavaIdentifierPart(c);
  }

  @Contract(pure = true)
  public static boolean isJavaIdentifier(@NotNull String text) {
    int len = text.length();
    if (len == 0) return false;

    if (!isJavaIdentifierStart(text.charAt(0))) return false;

    for (int i = 1; i < len; i++) {
      if (!isJavaIdentifierPart(text.charAt(i))) return false;
    }

    return true;
  }

  /**
   * Escape property name or key in property file. Unicode characters are escaped as well.
   *
   * @param input an input to escape
   * @param isKey if true, the rules for key escaping are applied. The leading space is escaped in that case.
   * @return an escaped string
   */
  @NotNull
  @Contract(pure = true)
  public static String escapeProperty(@NotNull String input, final boolean isKey) {
    final StringBuilder escaped = new StringBuilder(input.length());
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

  @Contract(pure = true)
  public static String getQualifiedName(@Nullable String packageName, String className) {
    if (packageName == null || packageName.isEmpty()) {
      return className;
    }
    return packageName + '.' + className;
  }

  @Contract(pure = true)
  public static int compareVersionNumbers(@Nullable String v1, @Nullable String v2) {
    // todo duplicates com.intellij.util.text.VersionComparatorUtil.compare
    // todo please refactor next time you make changes here
    if (v1 == null && v2 == null) {
      return 0;
    }
    if (v1 == null) {
      return -1;
    }
    if (v2 == null) {
      return 1;
    }

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
    else {
      boolean left = part1.length > idx;
      String[] parts = left ? part1 : part2;

      for (; idx < parts.length; idx++) {
        String p = parts[idx];
        int cmp;
        if (p.matches("\\d+")) {
          cmp = new Integer(p).compareTo(0);
        }
        else {
          cmp = 1;
        }
        if (cmp != 0) return left ? cmp : -cmp;
      }
      return 0;
    }
  }

  @Contract(pure = true)
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

  @Contract(pure = true)
  public static int getOccurrenceCount(@NotNull String text, @NotNull String s) {
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

  @Contract(pure = true)
  public static int getIgnoreCaseOccurrenceCount(@NotNull String text, @NotNull String s) {
    int res = 0;
    int i = 0;
    while (i < text.length()) {
      i = indexOfIgnoreCase(text, s, i);
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
  @Contract(pure = true)
  public static String fixVariableNameDerivedFromPropertyName(@NotNull String name) {
    if (isEmptyOrSpaces(name)) return name;
    char c = name.charAt(0);
    if (isVowel(c)) {
      return "an" + Character.toUpperCase(c) + name.substring(1);
    }
    return "a" + Character.toUpperCase(c) + name.substring(1);
  }

  @NotNull
  @Contract(pure = true)
  public static String sanitizeJavaIdentifier(@NotNull String name) {
    final StringBuilder result = new StringBuilder(name.length());

    for (int i = 0; i < name.length(); i++) {
      final char ch = name.charAt(i);
      if (Character.isJavaIdentifierPart(ch)) {
        if (result.length() == 0 && !Character.isJavaIdentifierStart(ch)) {
          result.append("_");
        }
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
      throw new AssertionError("Wrong line separators: '" + context + "' at offset " + slashRIndex);
    }
  }

  @NotNull
  @Contract(pure = true)
  public static String tail(@NotNull String s, final int idx) {
    return idx >= s.length() ? "" : s.substring(idx, s.length());
  }

  /**
   * Splits string by lines.
   *
   * @param string String to split
   * @return array of strings
   */
  @NotNull
  @Contract(pure = true)
  public static String[] splitByLines(@NotNull String string) {
    return splitByLines(string, true);
  }

  /**
   * Splits string by lines. If several line separators are in a row corresponding empty lines
   * are also added to result if {@code excludeEmptyStrings} is {@code false}.
   *
   * @param string String to split
   * @return array of strings
   */
  @NotNull
  @Contract(pure = true)
  public static String[] splitByLines(@NotNull String string, boolean excludeEmptyStrings) {
    return (excludeEmptyStrings ? EOL_SPLIT_PATTERN : EOL_SPLIT_PATTERN_WITH_EMPTY).split(string);
  }

  @NotNull
  @Contract(pure = true)
  public static String[] splitByLinesDontTrim(@NotNull String string) {
    return EOL_SPLIT_DONT_TRIM_PATTERN.split(string);
  }

  /**
   * Splits string by lines, keeping all line separators at the line ends and in the empty lines.
   * <br> E.g. splitting text
   * <blockquote>
   *   foo\r\n<br>
   *   \n<br>
   *   bar\n<br>
   *   \r\n<br>
   *   baz\r<br>
   *   \r<br>
   * </blockquote>
   * will return the following array: foo\r\n, \n, bar\n, \r\n, baz\r, \r
   *
   */
  @NotNull
  @Contract(pure = true)
  public static String[] splitByLinesKeepSeparators(@NotNull String string) {
    return EOL_SPLIT_KEEP_SEPARATORS.split(string);
  }

  @NotNull
  @Contract(pure = true)
  public static List<Pair<String, Integer>> getWordsWithOffset(@NotNull String s) {
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

  @Contract(pure = true)
  public static int naturalCompare(@Nullable String string1, @Nullable String string2) {
    return NaturalComparator.INSTANCE.compare(string1, string2);
  }

  @Contract(pure = true)
  public static boolean isDecimalDigit(char c) {
    return c >= '0' && c <= '9';
  }

  @Contract(pure = true)
  public static int compare(@Nullable String s1, @Nullable String s2, boolean ignoreCase) {
    //noinspection StringEquality
    if (s1 == s2) return 0;
    if (s1 == null) return -1;
    if (s2 == null) return 1;
    return ignoreCase ? s1.compareToIgnoreCase(s2) : s1.compareTo(s2);
  }

  @Contract(pure = true)
  public static int comparePairs(@Nullable String s1, @Nullable String t1, @Nullable String s2, @Nullable String t2, boolean ignoreCase) {
    final int compare = compare(s1, s2, ignoreCase);
    return compare != 0 ? compare : compare(t1, t2, ignoreCase);
  }

  @Contract(pure = true)
  public static int hashCode(@NotNull CharSequence s) {
    return stringHashCode(s);
  }

  @Contract(pure = true)
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

  @Contract(pure = true)
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
      if (!charsEqualIgnoreCase(s1.charAt(i), s2.charAt(i))) {
        return false;
      }
    }
    return true;
  }

  @Contract(pure = true)
  public static boolean equalsIgnoreWhitespaces(@Nullable CharSequence s1, @Nullable CharSequence s2) {
    if (s1 == null ^ s2 == null) {
      return false;
    }

    if (s1 == null) {
      return true;
    }

    int len1 = s1.length();
    int len2 = s2.length();

    int index1 = 0;
    int index2 = 0;
    while (index1 < len1 && index2 < len2) {
      if (s1.charAt(index1) == s2.charAt(index2)) {
        index1++;
        index2++;
        continue;
      }

      boolean skipped = false;
      while (index1 != len1 && isWhiteSpace(s1.charAt(index1))) {
        skipped = true;
        index1++;
      }
      while (index2 != len2 && isWhiteSpace(s2.charAt(index2))) {
        skipped = true;
        index2++;
      }

      if (!skipped) return false;
    }

    for (; index1 != len1; index1++) {
      if (!isWhiteSpace(s1.charAt(index1))) return false;
    }
    for (; index2 != len2; index2++) {
      if (!isWhiteSpace(s2.charAt(index2))) return false;
    }

    return true;
  }

  @Contract(pure = true)
  public static boolean equalsTrimWhitespaces(@NotNull CharSequence s1, @NotNull CharSequence s2) {
    int start1 = 0;
    int end1 = s1.length();
    int end2 = s2.length();

    while (start1 < end1) {
      char c = s1.charAt(start1);
      if (!isWhiteSpace(c)) break;
      start1++;
    }

    while (start1 < end1) {
      char c = s1.charAt(end1 - 1);
      if (!isWhiteSpace(c)) break;
      end1--;
    }

    int start2 = 0;
    while (start2 < end2) {
      char c = s2.charAt(start2);
      if (!isWhiteSpace(c)) break;
      start2++;
    }

    while (start2 < end2) {
      char c = s2.charAt(end2 - 1);
      if (!isWhiteSpace(c)) break;
      end2--;
    }

    CharSequence ts1 = new CharSequenceSubSequence(s1, start1, end1);
    CharSequence ts2 = new CharSequenceSubSequence(s2, start2, end2);

    return equals(ts1, ts2);
  }

  @Contract(pure = true)
  public static boolean findIgnoreCase(@Nullable String toFind, @NotNull String... where) {
    for (String string : where) {
      if (equalsIgnoreCase(toFind, string)) return true;
    }
    return false;
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

  @NotNull
  @Contract(pure = true)
  public static String formatLinks(@NotNull String message) {
    Pattern linkPattern = Pattern.compile("http://[a-zA-Z0-9\\./\\-\\+]+");
    StringBuffer result = new StringBuffer();
    Matcher m = linkPattern.matcher(message);
    while (m.find()) {
      m.appendReplacement(result, "<a href=\"" + m.group() + "\">" + m.group() + "</a>");
    }
    m.appendTail(result);
    return result.toString();
  }

  @Contract(pure = true)
  public static boolean isHexDigit(char c) {
    return '0' <= c && c <= '9' || 'a' <= c && c <= 'f' || 'A' <= c && c <= 'F';
  }

  @Contract(pure = true)
  public static boolean isOctalDigit(char c) {
    return '0' <= c && c <= '7';
  }

  @NotNull
  @Contract(pure = true)
  public static String shortenTextWithEllipsis(@NotNull final String text, final int maxLength, final int suffixLength) {
    return shortenTextWithEllipsis(text, maxLength, suffixLength, false);
  }

  @NotNull
  @Contract(pure = true)
  public static String trimMiddle(@NotNull String text, int maxLength) {
    return shortenTextWithEllipsis(text, maxLength, maxLength >> 1, true);
  }

  @NotNull
  @Contract(pure = true)
  public static String shortenTextWithEllipsis(@NotNull final String text,
                                               final int maxLength,
                                               final int suffixLength,
                                               @NotNull String symbol) {
    final int textLength = text.length();
    if (textLength > maxLength) {
      final int prefixLength = maxLength - suffixLength - symbol.length();
      assert prefixLength > 0;
      return text.substring(0, prefixLength) + symbol + text.substring(textLength - suffixLength);
    }
    else {
      return text;
    }
  }

  @NotNull
  @Contract(pure = true)
  public static String shortenTextWithEllipsis(@NotNull final String text,
                                               final int maxLength,
                                               final int suffixLength,
                                               boolean useEllipsisSymbol) {
    String symbol = useEllipsisSymbol ? "\u2026" : "...";
    return shortenTextWithEllipsis(text, maxLength, suffixLength, symbol);
  }

  @NotNull
  @Contract(pure = true)
  public static String shortenPathWithEllipsis(@NotNull final String path, final int maxLength, boolean useEllipsisSymbol) {
    return shortenTextWithEllipsis(path, maxLength, (int)(maxLength * 0.7), useEllipsisSymbol);
  }

  @NotNull
  @Contract(pure = true)
  public static String shortenPathWithEllipsis(@NotNull final String path, final int maxLength) {
    return shortenPathWithEllipsis(path, maxLength, false);
  }

  @Contract(pure = true)
  public static boolean charsEqualIgnoreCase(char a, char b) {
    return charsMatch(a, b, true);
  }

  @Contract(pure = true)
  public static char toUpperCase(char a) {
    return StringUtilRt.toUpperCase(a);
  }

  @Contract(value = "null -> null; !null -> !null", pure = true)
  public static String toUpperCase(String a) {
    return a == null ? null : StringUtilRt.toUpperCase(a).toString();
  }

  @Contract(pure = true)
  public static char toLowerCase(final char a) {
    return StringUtilRt.toLowerCase(a);
  }

  @Nullable
  public static LineSeparator detectSeparators(@NotNull CharSequence text) {
    int index = indexOfAny(text, "\n\r");
    if (index == -1) return null;
    LineSeparator lineSeparator = getLineSeparatorAt(text, index);
    if (lineSeparator == null) {
      throw new AssertionError();
    }
    return lineSeparator;
  }

  @Nullable
  public static LineSeparator getLineSeparatorAt(@NotNull CharSequence text, int index) {
    if (index < 0 || index >= text.length()) {
      return null;
    }
    char ch = text.charAt(index);
    if (ch == '\r') {
      return index + 1 < text.length() && text.charAt(index + 1) == '\n' ? LineSeparator.CRLF : LineSeparator.CR;
    }
    return ch == '\n' ? LineSeparator.LF : null;
  }

  @NotNull
  @Contract(pure = true)
  public static String convertLineSeparators(@NotNull String text) {
    return StringUtilRt.convertLineSeparators(text);
  }

  @NotNull
  @Contract(pure = true)
  public static String convertLineSeparators(@NotNull String text, boolean keepCarriageReturn) {
    return StringUtilRt.convertLineSeparators(text, keepCarriageReturn);
  }

  @NotNull
  @Contract(pure = true)
  public static String convertLineSeparators(@NotNull String text, @NotNull String newSeparator) {
    return StringUtilRt.convertLineSeparators(text, newSeparator);
  }

  @NotNull
  public static String convertLineSeparators(@NotNull String text, @NotNull String newSeparator, @Nullable int[] offsetsToKeep) {
    return StringUtilRt.convertLineSeparators(text, newSeparator, offsetsToKeep);
  }

  @Contract(pure = true)
  public static int parseInt(final String string, final int defaultValue) {
    return StringUtilRt.parseInt(string, defaultValue);
  }

  @Contract(pure = true)
  public static long parseLong(@Nullable String string, long defaultValue) {
    return StringUtilRt.parseLong(string, defaultValue);
  }

  @Contract(pure = true)
  public static double parseDouble(final String string, final double defaultValue) {
    return StringUtilRt.parseDouble(string, defaultValue);
  }

  @Contract(pure = true)
  public static boolean parseBoolean(String string, final boolean defaultValue) {
    return StringUtilRt.parseBoolean(string, defaultValue);
  }

  @Contract(pure = true)
  public static <E extends Enum<E>> E parseEnum(@NotNull String string, E defaultValue, @NotNull Class<E> clazz) {
    return StringUtilRt.parseEnum(string, defaultValue, clazz);
  }

  @NotNull
  @Contract(pure = true)
  public static String getShortName(@NotNull Class aClass) {
    return StringUtilRt.getShortName(aClass);
  }

  @NotNull
  @Contract(pure = true)
  public static String getShortName(@NotNull String fqName) {
    return StringUtilRt.getShortName(fqName);
  }

  @NotNull
  @Contract(pure = true)
  public static String getShortName(@NotNull String fqName, char separator) {
    return StringUtilRt.getShortName(fqName, separator);
  }

  /**
   * Strips class name from Object#toString if present.
   * To be used as custom data type renderer for java.lang.Object.
   * To activate just add {@code StringUtil.toShortString(this)}
   * expression in <em>Settings | Debugger | Data Views</em>.
   */
  @Contract("null->null;!null->!null")
  @SuppressWarnings("UnusedDeclaration")
  static String toShortString(@Nullable Object o) {
    if (o == null) return null;
    if (o instanceof CharSequence) return o.toString();
    String className = o.getClass().getName();
    String s = o.toString();
    if (!s.startsWith(className)) return s;
    return s.length() > className.length() && !Character.isLetter(s.charAt(className.length())) ?
           trimStart(s, className) : s;
  }

  @NotNull
  @Contract(pure = true)
  public static CharSequence newBombedCharSequence(@NotNull CharSequence sequence, long delay) {
    final long myTime = System.currentTimeMillis() + delay;
    return new BombedCharSequence(sequence) {
      @Override
      protected void checkCanceled() {
        long l = System.currentTimeMillis();
        if (l >= myTime) {
          throw new ProcessCanceledException();
        }
      }
    };
  }

  public static boolean trimEnd(@NotNull StringBuilder buffer, @NotNull CharSequence end) {
    if (endsWith(buffer, end)) {
      buffer.delete(buffer.length() - end.length(), buffer.length());
      return true;
    }
    return false;
  }

  /**
   * Say smallPart = "op" and bigPart="open". Method returns true for "Ope" and false for "ops"
   */
  @Contract(pure = true)
  public static boolean isBetween(@NotNull String string, @NotNull String smallPart, @NotNull String bigPart) {
    final String s = string.toLowerCase();
    return s.startsWith(smallPart.toLowerCase()) && bigPart.toLowerCase().startsWith(s);
  }

  /**
   * Does the string have an uppercase character?
   * @param s  the string to test.
   * @return   true if the string has an uppercase character, false if not.
   */
  public static boolean hasUpperCaseChar(String s) {
      char[] chars = s.toCharArray();
      for (char c : chars) {
          if (Character.isUpperCase(c)) {
              return true;
          }
      }
      return false;
  }

  /**
   * Does the string have a lowercase character?
   * @param s  the string to test.
   * @return   true if the string has a lowercase character, false if not.
   */
  public static boolean hasLowerCaseChar(String s) {
      char[] chars = s.toCharArray();
      for (char c : chars) {
          if (Character.isLowerCase(c)) {
              return true;
          }
      }
      return false;
  }

  private static final Pattern UNICODE_CHAR = Pattern.compile("\\\\u[0-9a-eA-E]{4}");

  public static String replaceUnicodeEscapeSequences(String text) {
    if (text == null) return null;

    final Matcher matcher = UNICODE_CHAR.matcher(text);
    if (!matcher.find()) return text; // fast path

    matcher.reset();
    int lastEnd = 0;
    final StringBuilder sb = new StringBuilder(text.length());
    while (matcher.find()) {
      sb.append(text.substring(lastEnd, matcher.start()));
      final char c = (char)Integer.parseInt(matcher.group().substring(2), 16);
      sb.append(c);
      lastEnd = matcher.end();
    }
    sb.append(text.substring(lastEnd, text.length()));
    return sb.toString();
  }

  /**
   * Expirable CharSequence. Very useful to control external library execution time,
   * i.e. when java.util.regex.Pattern match goes out of control.
   */
  public abstract static class BombedCharSequence implements CharSequence {
    private final CharSequence delegate;
    private int i;

    public BombedCharSequence(@NotNull CharSequence sequence) {
      delegate = sequence;
    }

    @Override
    public int length() {
      check();
      return delegate.length();
    }

    @Override
    public char charAt(int i) {
      check();
      return delegate.charAt(i);
    }

    protected void check() {
      if ((++i & 1023) == 0) {
        checkCanceled();
      }
    }

    @NotNull
    @Override
    public String toString() {
      check();
      return delegate.toString();
    }

    protected abstract void checkCanceled();

    @NotNull
    @Override
    public CharSequence subSequence(int i, int i1) {
      check();
      return delegate.subSequence(i, i1);
    }
  }

  @Contract(pure = true)
  @NotNull
  @SuppressWarnings("SpellCheckingInspection")
  public static String toHexString(@NotNull byte[] bytes) {
    String digits = "0123456789abcdef";
    StringBuilder sb = new StringBuilder(2 * bytes.length);
    for (byte b : bytes) sb.append(digits.charAt((b >> 4) & 0xf)).append(digits.charAt(b & 0xf));
    return sb.toString();
  }

  /** @deprecated use {@link #startsWithConcatenation(String, String...)} (to remove in IDEA 15) */
  public static boolean startsWithConcatenationOf(@NotNull String string, @NotNull String firstPrefix, @NotNull String secondPrefix) {
    return startsWithConcatenation(string, firstPrefix, secondPrefix);
  }
}
