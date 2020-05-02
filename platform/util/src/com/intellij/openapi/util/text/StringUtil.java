// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.util.text;

import com.intellij.ReviseWhenPortedToJDK;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.util.*;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.text.*;
import gnu.trove.TIntArrayList;
import gnu.trove.TLongArrayList;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Contract;
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
import java.util.StringTokenizer;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collector;
import java.util.stream.Collectors;

//TeamCity inherits StringUtil: do not add private constructors!!!
@SuppressWarnings("MethodOverridesStaticMethodOfSuperclass")
public class StringUtil extends StringUtilRt {
  @SuppressWarnings("SpellCheckingInspection") private static final String VOWELS = "aeiouy";
  private static final Pattern EOL_SPLIT_KEEP_SEPARATORS = Pattern.compile("(?<=(\r\n|\n))|(?<=\r)(?=[^\n])");
  private static final Pattern EOL_SPLIT_PATTERN = Pattern.compile(" *(\r|\n|\r\n)+ *");
  private static final Pattern EOL_SPLIT_PATTERN_WITH_EMPTY = Pattern.compile(" *(\r|\n|\r\n) *");
  private static final Pattern EOL_SPLIT_DONT_TRIM_PATTERN = Pattern.compile("(\r|\n|\r\n)+");
  public static final String ELLIPSIS = "\u2026";

  /**
   * @return a lightweight CharSequence which results from replacing {@code [start, end)} range in the {@code charSeq} with {@code replacement}.
   * Works in O(1), but retains references to the passed char sequences, so please use something else if you want them to be garbage-collected.
   */
  public static @NotNull MergingCharSequence replaceSubSequence(@NotNull CharSequence charSeq, int start, int end, @NotNull CharSequence replacement) {
    return new MergingCharSequence(
          new MergingCharSequence(new CharSequenceSubSequence(charSeq, 0, start), replacement),
          new CharSequenceSubSequence(charSeq, end, charSeq.length()));
  }

  private static final class MyHtml2Text extends HTMLEditorKit.ParserCallback {
    private final @NotNull StringBuilder myBuffer = new StringBuilder();
    private final boolean myIsSkipStyleTag;

    private boolean myIsStyleTagOpened;

    private MyHtml2Text(boolean isSkipStyleTag) {
      myIsSkipStyleTag = isSkipStyleTag;
    }

    public void parse(@NotNull Reader in) throws IOException {
      myBuffer.setLength(0);
      new ParserDelegator().parse(in, this, Boolean.TRUE);
    }

    @Override
    public void handleText(char @NotNull [] text, int pos) {
      if (!myIsStyleTagOpened) {
        myBuffer.append(text);
      }
    }

    @Override
    public void handleStartTag(@NotNull HTML.Tag tag, MutableAttributeSet set, int i) {
      if (myIsSkipStyleTag && "style".equals(tag.toString())) {
        myIsStyleTagOpened = true;
      }
      handleTag(tag);
    }

    @Override
    public void handleEndTag(@NotNull HTML.Tag tag, int pos) {
      if (myIsSkipStyleTag && "style".equals(tag.toString())) {
        myIsStyleTagOpened = false;
      }
    }

    @Override
    public void handleSimpleTag(HTML.Tag tag, MutableAttributeSet set, int i) {
      handleTag(tag);
    }

    private void handleTag(@NotNull HTML.Tag tag) {
      if (tag.breaksFlow() && myBuffer.length() > 0) {
        myBuffer.append(SystemProperties.getLineSeparator());
      }
    }

    public @NotNull String getText() {
      return myBuffer.toString();
    }
  }

  private static final MyHtml2Text html2TextParser = new MyHtml2Text(false);

  public static final NotNullFunction<String, String> QUOTER = s -> "\"" + s + "\"";

  public static final NotNullFunction<String, String> SINGLE_QUOTER = s -> "'" + s + "'";

  @Contract(pure = true)
  public static @NotNull List<String> getWordsInStringLongestFirst(@NotNull String find) {
    List<String> words = getWordsIn(find);
    // hope long words are rare
    words.sort((o1, o2) -> o2.length() - o1.length());
    return words;
  }

  @Contract(pure = true)
  public static @NotNull String escapePattern(final @NotNull String text) {
    return replace(replace(text, "'", "''"), "{", "'{'");
  }

  @Contract(pure = true)
  public static @NotNull <T> Function<T, String> createToStringFunction(@SuppressWarnings("unused") @NotNull Class<T> cls) {
    return Object::toString;
  }

  public static final @NotNull Function<String, String> TRIMMER = StringUtil::trim;

  // Unlike String.replace(CharSequence,CharSequence) does not allocate intermediate objects on non-match
  // TODO revise when JDK9 arrives - its String.replace(CharSequence, CharSequence) is more optimized
  @ReviseWhenPortedToJDK("9")
  @Contract(pure = true)
  public static @NotNull String replace(@NotNull String text, @NotNull String oldS, @NotNull String newS) {
    return replace(text, oldS, newS, false);
  }

  @Contract(pure = true)
  public static @NotNull String replaceIgnoreCase(@NotNull String text, @NotNull String oldS, @NotNull String newS) {
    return replace(text, oldS, newS, true);
  }

  /**
   * @deprecated Use {@link String#replace(char,char)} instead
   */
  @Contract(pure = true)
  @Deprecated
  public static @NotNull String replaceChar(@NotNull String buffer, char oldChar, char newChar) {
    return buffer.replace(oldChar, newChar);
  }

  @Contract(pure = true)
  public static String replace(final @NotNull String text, final @NotNull String oldS, final @NotNull String newS, final boolean ignoreCase) {
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
  public static int lastIndexOfIgnoreCase(@NotNull String where, char what, int fromIndex) {
    for (int i = Math.min(fromIndex, where.length() - 1); i >= 0; i--) {
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
  public static boolean endsWithIgnoreCase(@NotNull String str, @NotNull String suffix) {
    return StringUtilRt.endsWithIgnoreCase(str, suffix);
  }

  @Contract(pure = true)
  public static boolean startsWithIgnoreCase(@NotNull String str, @NotNull String prefix) {
    return StringUtilRt.startsWithIgnoreCase(str, prefix);
  }

  @Contract(pure = true)
  public static @NotNull String stripHtml(@NotNull String html, boolean convertBreaks) {
    if (convertBreaks) {
      html = html.replaceAll("<br/?>", "\n\n");
    }

    return html.replaceAll("<(.|\n)*?>", "");
  }

  @Contract(value = "null -> null; !null -> !null", pure = true)
  public static String toLowerCase(@Nullable String str) {
    return str == null ? null : str.toLowerCase(Locale.ENGLISH);
  }

  @Contract(pure = true)
  public static @NotNull String getPackageName(@NotNull String fqName) {
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
  @Contract(pure = true)
  public static @NotNull String getPackageName(@NotNull String fqName, char separator) {
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
        }
        count++;
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

  @Contract(pure = true)
  public static @NotNull String escapeLineBreak(@NotNull String text) {
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
    LineColumn lineColumn = offsetToLineColumn(text, offset);
    return lineColumn != null ? lineColumn.line : -1;
  }

  @Contract(pure = true)
  public static LineColumn offsetToLineColumn(@NotNull CharSequence text, int offset) {
    int curLine = 0;
    int curLineStart = 0;
    int curOffset = 0;
    while (curOffset < offset) {
      if (curOffset == text.length()) return null;
      char c = text.charAt(curOffset);
      if (c == '\n') {
        curLine++;
        curLineStart = curOffset + 1;
      }
      else if (c == '\r') {
        curLine++;
        if (curOffset < text.length() - 1 && text.charAt(curOffset + 1) == '\n') {
          curOffset++;
        }
        curLineStart = curOffset + 1;
      }
      curOffset++;
    }

    return LineColumn.of(curLine, offset - curLineStart);
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

  @Contract(pure = true)
  public static @NotNull String wordsToBeginFromUpperCase(@NotNull String s) {
    return fixCapitalization(s, ArrayUtil.mergeArrays(ourPrepositions, ourOtherNonCapitalizableWords), true);
  }

  @Contract(pure = true)
  public static @NotNull String wordsToBeginFromLowerCase(@NotNull String s) {
    return fixCapitalization(s, ourPrepositions, false);
  }

  @Contract(pure = true)
  public static @NotNull String toTitleCase(@NotNull String s) {
    return fixCapitalization(s, ArrayUtilRt.EMPTY_STRING_ARRAY, true);
  }

  private static @NotNull String fixCapitalization(@NotNull String s, String @NotNull [] prepositions, boolean title) {
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

  private static final String[] ourPrepositions = {
    "a", "an", "and", "as", "at", "but", "by", "down", "for", "from", "if", "in", "into", "not", "of", "on", "onto", "or", "out", "over",
    "per", "nor", "the", "to", "up", "upon", "via", "with"
  };

  private static final String[] ourOtherNonCapitalizableWords = {
    "iOS", "iPhone", "iPad", "iMac"
  };

  /**
   * @deprecated Use {@link #isPreposition(String, int, int, String[])}.
   */
  @ApiStatus.ScheduledForRemoval(inVersion = "2020.1")
  @Deprecated
  @Contract(pure = true)
  public static boolean isPreposition(@NotNull String s, int firstChar, int lastChar) {
    return isPreposition(s, firstChar, lastChar, ourPrepositions);
  }

  @Contract(pure = true)
  public static boolean isPreposition(@NotNull String s, int firstChar, int lastChar, String @NotNull [] prepositions) {
    for (String preposition : prepositions) {
      boolean found = false;
      if (lastChar - firstChar + 1 == preposition.length()) {
        found = true;
        for (int j = 0; j < preposition.length(); j++) {
          if (toLowerCase(s.charAt(firstChar + j)) != toLowerCase(preposition.charAt(j))) {
            found = false;
            break;
          }
        }
      }
      if (found) {
        return true;
      }
    }
    return false;
  }

  @Contract(pure = true)
  public static @NotNull NotNullFunction<String, String> escaper(final boolean escapeSlash, final @Nullable String additionalChars) {
    return dom -> {
      final StringBuilder builder = new StringBuilder(dom.length());
      escapeStringCharacters(dom.length(), dom, additionalChars, escapeSlash, builder);
      return builder.toString();
    };
  }


  public static void escapeStringCharacters(int length, @NotNull String str, @NotNull StringBuilder buffer) {
    escapeStringCharacters(length, str, "\"", buffer);
  }

  public static @NotNull StringBuilder escapeStringCharacters(int length,
                                                              @NotNull String str,
                                                              @Nullable String additionalChars,
                                                              @NotNull StringBuilder buffer) {
    return escapeStringCharacters(length, str, additionalChars, true, buffer);
  }

  public static @NotNull StringBuilder escapeStringCharacters(int length,
                                                              @NotNull String str,
                                                              @Nullable String additionalChars,
                                                              boolean escapeSlash,
                                                              @NotNull StringBuilder buffer) {
    return escapeStringCharacters(length, str, additionalChars, escapeSlash, true, buffer);
  }

  public static @NotNull StringBuilder escapeStringCharacters(int length,
                                                              @NotNull String str,
                                                              @Nullable String additionalChars,
                                                              boolean escapeSlash,
                                                              boolean escapeUnicode,
                                                              @NotNull StringBuilder buffer) {
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
            CharSequence hexCode = toUpperCase(Integer.toHexString(ch));
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

  @Contract(pure = true)
  public static @NotNull String escapeStringCharacters(@NotNull String s) {
    StringBuilder buffer = new StringBuilder(s.length());
    escapeStringCharacters(s.length(), s, "\"", buffer);
    return buffer.toString();
  }

  @Contract(pure = true)
  public static @NotNull String escapeCharCharacters(@NotNull String s) {
    StringBuilder buffer = new StringBuilder(s.length());
    escapeStringCharacters(s.length(), s, "'", buffer);
    return buffer.toString();
  }

  @Contract(pure = true)
  public static @NotNull String unescapeStringCharacters(@NotNull String s) {
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
    return StringUtilRt.isQuotedString(s);
  }

  /**
   * @return string with paired quotaion marks (quote (") or apostrophe (')) removed
   */
  @Contract(pure = true)
  public static @NotNull String unquoteString(@NotNull String s) {
    return StringUtilRt.unquoteString(s);
  }

  @Contract(pure = true)
  public static @NotNull String unquoteString(@NotNull String s, char quotationChar) {
    return StringUtilRt.unquoteString(s, quotationChar);
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
            //noinspection fallthrough
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

  @Contract(pure = true)
  public static @NotNull String pluralize(@NotNull String word) {
    String plural = Pluralizer.PLURALIZER.plural(word);
    if (plural != null) return plural;
    if (word.endsWith("s")) return Pluralizer.restoreCase(word, word + "es");
    return Pluralizer.restoreCase(word, word + "s");
  }

  @Contract(pure = true)
  public static @NotNull String capitalizeWords(@NotNull String text,
                                                boolean allWords) {
    return capitalizeWords(text, " \t\n\r\f([<", allWords, true);
  }

  @Contract(pure = true)
  public static @NotNull String capitalizeWords(@NotNull String text,
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
  public static @NotNull String decapitalize(@NotNull String s) {
    return Introspector.decapitalize(s);
  }

  @Contract(pure = true)
  public static boolean isVowel(char c) {
    return VOWELS.indexOf(c) >= 0;
  }

  /**
   * Capitalize the first letter of the sentence.
   */
  @Contract(pure = true)
  public static @NotNull String capitalize(@NotNull String s) {
    if (s.isEmpty()) return s;
    if (s.length() == 1) return toUpperCase(s);

    // Optimization
    if (Character.isUpperCase(s.charAt(0))) return s;
    return toUpperCase(s.charAt(0)) + s.substring(1);
  }

  @Contract(value = "null -> false", pure = true)
  public static boolean isCapitalized(@Nullable String s) {
    return s != null && !s.isEmpty() && Character.isUpperCase(s.charAt(0));
  }

  @Contract(pure = true)
  public static @NotNull String capitalizeWithJavaBeanConvention(@NotNull String s) {
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

  @Contract(pure = true)
  public static int stringHashCodeIgnoreWhitespaces(char @NotNull [] chars, int from, int to) {
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
  public static boolean startsWithConcatenation(@NotNull String string, String @NotNull ... prefixes) {
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
  public static @NotNull String trimLog(final @NotNull String text, final int limit) {
    if (limit > 5 && text.length() > limit) {
      return text.substring(0, limit - 5) + " ...\n";
    }
    return text;
  }

  @Contract(pure = true)
  public static @NotNull String trimLeading(@NotNull String string) {
    return trimLeading((CharSequence)string).toString();
  }
  @Contract(pure = true)
  public static @NotNull CharSequence trimLeading(@NotNull CharSequence string) {
    int index = 0;
    while (index < string.length() && Character.isWhitespace(string.charAt(index))) index++;
    return string.subSequence(index, string.length());
  }

  @Contract(pure = true)
  public static @NotNull String trimLeading(@NotNull String string, char symbol) {
    int index = 0;
    while (index < string.length() && string.charAt(index) == symbol) index++;
    return string.substring(index);
  }

  public static @NotNull StringBuilder trimLeading(@NotNull StringBuilder builder, char symbol) {
    int index = 0;
    while (index < builder.length() && builder.charAt(index) == symbol) index++;
    if (index > 0) builder.delete(0, index);
    return builder;
  }

  @Contract(pure = true)
  public static @NotNull String trimTrailing(@NotNull String string) {
    return trimTrailing((CharSequence)string).toString();
  }

  @Contract(pure = true)
  public static @NotNull CharSequence trimTrailing(@NotNull CharSequence string) {
    int index = string.length() - 1;
    while (index >= 0 && Character.isWhitespace(string.charAt(index))) index--;
    return string.subSequence(0, index + 1);
  }

  @Contract(pure = true)
  public static @NotNull String trimTrailing(@NotNull String string, char symbol) {
    int index = string.length() - 1;
    while (index >= 0 && string.charAt(index) == symbol) index--;
    return string.substring(0, index + 1);
  }

  public static @NotNull StringBuilder trimTrailing(@NotNull StringBuilder builder, char symbol) {
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

  @Contract(pure = true)
  public static @NotNull String trimStart(@NotNull String s, @NotNull String prefix) {
    if (s.startsWith(prefix)) {
      return s.substring(prefix.length());
    }
    return s;
  }

  @Contract(pure = true)
  public static @NotNull String trimExtensions(@NotNull String name) {
    int index = name.indexOf('.');
    return index < 0 ? name : name.substring(0, index);
  }

  @Contract(pure = true)
  public static @NotNull String pluralize(@NotNull String base, int count) {
    if (count == 1) return base;
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
      Logger.getInstance(StringUtil.class).error(e);
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
    return StringUtilRt.isEmpty(cs);
  }

  @Contract(pure = true)
  public static int length(@Nullable CharSequence cs) {
    return cs == null ? 0 : cs.length();
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

  @Contract(value = "null -> true",pure = true)
  // we need to keep this method to preserve backward compatibility
  public static boolean isEmptyOrSpaces(@Nullable String s) {
    return isEmptyOrSpaces((CharSequence)s);
  }

  @Contract(value = "null -> true", pure = true)
  public static boolean isEmptyOrSpaces(@Nullable CharSequence s) {
    return StringUtilRt.isEmptyOrSpaces(s);
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

  @Contract(pure = true)
  public static @NotNull String getThrowableText(@NotNull Throwable aThrowable) {
    return ExceptionUtil.getThrowableText(aThrowable);
  }

  @Contract(pure = true)
  public static @Nullable String getMessage(@NotNull Throwable e) {
    return ExceptionUtil.getMessage(e);
  }

  @Contract(pure = true)
  public static @NotNull String repeatSymbol(final char aChar, final int count) {
    char[] buffer = new char[count];
    Arrays.fill(buffer, aChar);
    return StringFactory.createShared(buffer);
  }

  @Contract(pure = true)
  public static @NotNull String repeat(@NotNull String s, int count) {
    if (count == 0) return "";
    assert count >= 0 : count;
    StringBuilder sb = new StringBuilder(s.length() * count);
    for (int i = 0; i < count; i++) {
      sb.append(s);
    }
    return sb.toString();
  }

  @Contract(pure = true)
  public static @NotNull List<String> splitHonorQuotes(@NotNull String s, char separator) {
    return StringUtilRt.splitHonorQuotes(s, separator);
  }


  @Contract(pure = true)
  public static @NotNull List<String> split(@NotNull String s, @NotNull String separator) {
    return split(s, separator, true);
  }
  @Contract(pure = true)
  public static @NotNull List<CharSequence> split(@NotNull CharSequence s, @NotNull CharSequence separator) {
    return split(s, separator, true, true);
  }

  @Contract(pure = true)
  public static @NotNull List<String> split(@NotNull String s, @NotNull String separator, boolean excludeSeparator) {
    return split(s, separator, excludeSeparator, true);
  }

  @Contract(pure = true)
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
      int index = indexOf(s, separator, pos);
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
  public static @NotNull Iterable<String> tokenize(@NotNull String s, @NotNull String separators) {
    final com.intellij.util.text.StringTokenizer tokenizer = new com.intellij.util.text.StringTokenizer(s, separators);
    return () -> new Iterator<String>() {
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

  @Contract(pure = true)
  public static @NotNull Iterable<String> tokenize(final @NotNull StringTokenizer tokenizer) {
    return () -> new Iterator<String>() {
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

  /**
   * @return list containing all words in {@code text}, or {@link ContainerUtil#emptyList()} if there are none.
   * The <b>word</b> here means the maximum sub-string consisting entirely of characters which are {@code Character.isJavaIdentifierPart(c)}.
   */
  @Contract(pure = true)
  public static @NotNull List<String> getWordsIn(@NotNull String text) {
    List<String> result = null;
    int start = -1;
    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);
      boolean isIdentifierPart = Character.isJavaIdentifierPart(c);
      if (isIdentifierPart && start == -1) {
        start = i;
      }
      if (isIdentifierPart && i == text.length() - 1) {
        if (result == null) {
          result = new SmartList<>();
        }
        result.add(text.substring(start, i + 1));
      }
      else if (!isIdentifierPart && start != -1) {
        if (result == null) {
          result = new SmartList<>();
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

  @Contract(pure = true)
  public static @NotNull List<TextRange> getWordIndicesIn(@NotNull String text) {
    return getWordIndicesIn(text, null);
  }

  /**
   * @param text text to get word ranges in.
   * @param separatorsSet if not null, only these characters will be considered as separators (i.e. not a part of word).
   *                   Otherwise {@link Character#isJavaIdentifierPart(char)} will be used to determine whether a symbol is part of word.
   * @return ranges ranges of words in passed text.
   */
  @Contract(pure = true)
  public static @NotNull List<TextRange> getWordIndicesIn(@NotNull String text, @Nullable Set<Character> separatorsSet) {
    List<TextRange> result = new SmartList<>();
    int start = -1;
    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);
      boolean isIdentifierPart = separatorsSet == null ? Character.isJavaIdentifierPart(c) : !separatorsSet.contains(c);
      if (isIdentifierPart && start == -1) {
        start = i;
      }
      if (isIdentifierPart && i == text.length() - 1) {
        result.add(new TextRange(start, i + 1));
      }
      else if (!isIdentifierPart && start != -1) {
        result.add(new TextRange(start, i));
        start = -1;
      }
    }
    return result;
  }

  @Contract(pure = true)
  public static @NotNull String join(final String @NotNull [] strings, final @NotNull String separator) {
    return join(strings, 0, strings.length, separator);
  }

  @Contract(pure = true)
  public static @NotNull String join(final String @NotNull [] strings, int startIndex, int endIndex, final @NotNull String separator) {
    final StringBuilder result = new StringBuilder();
    for (int i = startIndex; i < endIndex; i++) {
      if (i > startIndex) result.append(separator);
      result.append(strings[i]);
    }
    return result.toString();
  }

  @Contract(pure = true)
  public static String @NotNull [] zip(String @NotNull [] strings1, String @NotNull [] strings2, String separator) {
    if (strings1.length != strings2.length) throw new IllegalArgumentException();

    String[] result = ArrayUtil.newStringArray(strings1.length);
    for (int i = 0; i < result.length; i++) {
      result[i] = strings1[i] + separator + strings2[i];
    }

    return result;
  }

  @Contract(pure = true)
  public static String @NotNull [] surround(String @NotNull [] strings, @NotNull String prefix, @NotNull String suffix) {
    String[] result = ArrayUtil.newStringArray(strings.length);
    for (int i = 0; i < result.length; i++) {
      result[i] = prefix + strings[i] + suffix;
    }
    return result;
  }

  @Contract(pure = true)
  public static @NotNull <T> String join(T @NotNull [] items, @NotNull Function<? super T, String> f, @NotNull String separator) {
    return join(Arrays.asList(items), f, separator);
  }

  @Contract(pure = true)
  public static @NotNull <T> String join(@NotNull Collection<? extends T> items,
                                         @NotNull Function<? super T, String> f,
                                         @NotNull String separator) {
    if (items.isEmpty()) return "";
    if (items.size() == 1) return notNullize(f.fun(items.iterator().next()));
    return join((Iterable<? extends T>)items, f, separator);
  }

  @Contract(pure = true)
  public static @NotNull String join(@NotNull Iterable<?> items, @NotNull String separator) {
    StringBuilder result = new StringBuilder();
    for (Object item : items) {
      result.append(item).append(separator);
    }
    if (result.length() > 0) {
      result.setLength(result.length() - separator.length());
    }
    return result.toString();
  }

  @Contract(pure = true)
  public static @NotNull <T> String join(@NotNull Iterable<? extends T> items,
                                         @NotNull Function<? super T, ? extends CharSequence> f,
                                         @NotNull String separator) {
    StringBuilder result = new StringBuilder();
    join(items, f, separator, result);
    return result.toString();
  }

  public static <T> void join(@NotNull Iterable<? extends T> items,
                              @NotNull Function<? super T, ? extends CharSequence> f,
                              @NotNull String separator,
                              @NotNull StringBuilder result) {
    boolean isFirst = true;
    for (T item : items) {
      CharSequence string = f.fun(item);
      if (!isEmpty(string)) {
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

  @Contract(pure = true)
  public static @NotNull String join(@NotNull Collection<String> strings, @NotNull String separator) {
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

  @Contract(pure = true)
  public static @NotNull String join(final int @NotNull [] strings, final @NotNull String separator) {
    final StringBuilder result = new StringBuilder();
    for (int i = 0; i < strings.length; i++) {
      if (i > 0) result.append(separator);
      result.append(strings[i]);
    }
    return result.toString();
  }

  @Contract(pure = true)
  public static @NotNull String join(final String @NotNull ... strings) {
    if (strings.length == 0) return "";

    final StringBuilder builder = new StringBuilder();
    for (final String string : strings) {
      builder.append(string);
    }
    return builder.toString();
  }

  @Contract(pure = true)
  public static @NotNull Collector<CharSequence, ?, String> joining() {
    return Collectors.joining(", ");
  }

  /**
   * Consider using {@link StringUtil#unquoteString(String)} instead.
   * Note: this method has an odd behavior:
   *   Quotes are removed even if leading and trailing quotes are different or
   *                           if there is only one quote (leading or trailing).
   */
  @Contract(pure = true)
  public static @NotNull String stripQuotesAroundValue(@NotNull String text) {
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

  /** Formats given file size in metric (1 kB = 1000 B) units (example: {@code formatFileSize(1234) = "1.23 KB"}). */
  @Contract(pure = true)
  public static @NotNull String formatFileSize(long fileSize) {
    return StringUtilRt.formatFileSize(fileSize);
  }

  /** Formats given file size in metric (1 kB = 1000 B) units (example: {@code formatFileSize(1234, "") = "1.23KB"}). */
  @Contract(pure = true)
  public static @NotNull String formatFileSize(long fileSize, @NotNull String unitSeparator) {
    return StringUtilRt.formatFileSize(fileSize, unitSeparator);
  }

  /** Formats duration given in milliseconds as a sum of time units (example: {@code formatDuration(123456) = "2 m 3 s 456 ms"}). */
  @Contract(pure = true)
  public static @NotNull String formatDuration(long duration) {
    return formatDuration(duration, " ");
  }

  private static final String[] TIME_UNITS = {"ms", "s", "m", "h", "d"};
  private static final long[] TIME_MULTIPLIERS = {1, 1000, 60, 60, 24};

  /** Formats duration given in milliseconds as a sum of time units (example: {@code formatDuration(123456, "") = "2m 3s 456ms"}). */
  @Contract(pure = true)
  public static @NotNull String formatDuration(long duration, @NotNull String unitSeparator) {
    return formatDuration(duration, unitSeparator, Integer.MAX_VALUE);
  }

  @Contract(pure = true)
  private static @NotNull String formatDuration(long duration, @NotNull String unitSeparator, int maxFragments) {
    TLongArrayList unitValues = new TLongArrayList();
    TIntArrayList unitIndices = new TIntArrayList();

    long count = duration;
    int i = 1;
    for (; i < TIME_UNITS.length && count > 0; i++) {
      long multiplier = TIME_MULTIPLIERS[i];
      if (count < multiplier) break;
      long remainder = count % multiplier;
      count /= multiplier;
      if (remainder != 0 || !unitValues.isEmpty()) {
        unitValues.insert(0, remainder);
        unitIndices.insert(0, i - 1);
      }
    }
    unitValues.insert(0, count);
    unitIndices.insert(0, i - 1);

    if (unitValues.size() > maxFragments) {
      int lastUnitIndex = unitIndices.get(maxFragments - 1);
      long lastMultiplier = TIME_MULTIPLIERS[lastUnitIndex];
      // Round up if needed
      if (unitValues.get(maxFragments) > lastMultiplier / 2) {
        long increment = lastMultiplier - unitValues.get(maxFragments);
        for (int unit = lastUnitIndex - 1; unit > 0; unit--) {
          increment *= TIME_MULTIPLIERS[unit];
        }
        return formatDuration(duration + increment, unitSeparator, maxFragments);
      }
    }

    StringBuilder result = new StringBuilder();
    for (i = 0; i < unitValues.size() && i < maxFragments; i++) {
      if (i > 0) result.append(" ");
      result.append(unitValues.get(i)).append(unitSeparator).append(TIME_UNITS[unitIndices.get(i)]);
    }
    return result.toString();
  }

  private static final String[] PADDED_FORMATS = {"%03d", "%02d", "%02d", "%02d", "%d"};
  /**
   * Formats duration given in milliseconds as a sum of padded time units, except the most significant unit
   * E.g. 234523598 padded as "2 d 03 h 11 min 04 sec 004 ms" accordingly with zeros except "days" here.
   */
  @Contract(pure = true)
  public static @NotNull String formatDurationPadded(long millis, @NotNull String unitSeparator) {
    StringBuilder result = new StringBuilder();

    long millisIn = 1;
    int i;
    for (i=1; i < TIME_MULTIPLIERS.length; i++) {
      long multiplier = TIME_MULTIPLIERS[i];
      millisIn *= multiplier;
      if (millis < millisIn) {
        break;
      }
    }
    long d = millis;
    for (i-=1; i >= 0; i--) {
      long multiplier = i==TIME_MULTIPLIERS.length-1 ? 1 : TIME_MULTIPLIERS[i+1];
      millisIn /= multiplier;
      long value = d / millisIn;
      d = d % millisIn;
      String format = result.length() == 0 ? "%d" : PADDED_FORMATS[i]; // do not pad the most significant unit
      if (result.length() != 0) result.append(" ");
      result.append(String.format(format, value)).append(unitSeparator).append(TIME_UNITS[i]);
    }
    return result.toString();
  }

  /**
   * Formats duration given in milliseconds as a sum of time units with at most two units
   * (example: {@code formatDuration(123456) = "2 m 3 s"}).
   */
  @Contract(pure = true)
  public static @NotNull String formatDurationApproximate(long duration) {
    return formatDuration(duration, " ", 2);
  }

  /**
   * Appends English ordinal suffix to the given number.
   */
  @Contract(pure = true)
  public static @NotNull String formatOrdinal(long num) {
    return OrdinalFormat.formatEnglish(num);
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
  public static boolean containsAlphaCharacters(@NotNull String value) {
    for (int i = 0; i < value.length(); i++) {
      if (Character.isLetter(value.charAt(i))) return true;
    }
    return false;
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
  public static boolean containsChar(final @NotNull String value, final char ch) {
    return value.indexOf(ch) >= 0;
  }

  /**
   * @deprecated use #capitalize(String)
   */
  @Deprecated
  @Contract(value = "null -> null; !null -> !null", pure = true)
  public static String firstLetterToUpperCase(final @Nullable String displayString) {
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
  @Contract(pure = true)
  public static @NotNull String strip(final @NotNull String s, final @NotNull CharFilter filter) {
    final StringBuilder result = new StringBuilder(s.length());
    for (int i = 0; i < s.length(); i++) {
      char ch = s.charAt(i);
      if (filter.accept(ch)) {
        result.append(ch);
      }
    }
    return result.toString();
  }

  /**
   * Trim all characters not accepted by given filter
   *
   * @param s      e.g. "/n    my string "
   * @param filter e.g. {@link CharFilter#NOT_WHITESPACE_FILTER}
   * @return trimmed string e.g. "my string"
   */
  @Contract(pure = true)
  public static @NotNull String trim(final @NotNull String s, final @NotNull CharFilter filter) {
    int start = 0;
    int end = s.length();

    for (; start < end; start++) {
      char ch = s.charAt(start);
      if (filter.accept(ch)) break;
    }

    for (; start < end; end--) {
      char ch = s.charAt(end - 1);
      if (filter.accept(ch)) break;
    }

    return s.substring(start, end);
  }

  @Contract(pure = true)
  public static @NotNull List<String> findMatches(@NotNull String s, @NotNull Pattern pattern) {
    return findMatches(s, pattern, 1);
  }

  @Contract(pure = true)
  public static @NotNull List<String> findMatches(@NotNull String s, @NotNull Pattern pattern, int groupIndex) {
    List<String> result = new SmartList<>();
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
  public static int findFirst(final @NotNull CharSequence s, @NotNull CharFilter filter) {
    for (int i = 0; i < s.length(); i++) {
      char ch = s.charAt(i);
      if (filter.accept(ch)) {
        return i;
      }
    }
    return -1;
  }

  @Contract(pure = true)
  public static @NotNull String replaceSubstring(@NotNull String string, @NotNull TextRange range, @NotNull String replacement) {
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
  public static boolean endsWith(@NotNull CharSequence text, int start, int end, @NotNull CharSequence suffix) {
    int suffixLen = suffix.length();
    if (end < suffixLen) return false;

    for (int i = end - 1; i >= end - suffixLen && i >= start; i--) {
      if (text.charAt(i) != suffix.charAt(i + suffixLen - end)) return false;
    }

    return true;
  }

  @Contract(pure = true)
  public static @NotNull String commonPrefix(@NotNull String s1, @NotNull String s2) {
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

  @Contract(pure = true)
  public static @NotNull String commonSuffix(@NotNull String s1, @NotNull String s2) {
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
  public static int indexOf(char @NotNull [] s, char c, int start, int end, boolean caseSensitive) {
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
    end = Math.min(end, s.length());
    for (int i = Math.max(start, 0); i < end; i++) {
      if (containsChar(chars, s.charAt(i))) return i;
    }
    return -1;
  }

  @Contract(pure = true)
  public static int lastIndexOfAny(@NotNull CharSequence s, final @NotNull String chars) {
    for (int i = s.length() - 1; i >= 0; i--) {
      if (containsChar(chars, s.charAt(i))) return i;
    }
    return -1;
  }

  @Contract(pure = true)
  public static @Nullable String substringBefore(@NotNull String text, @NotNull String subString) {
    int i = text.indexOf(subString);
    if (i == -1) return null;
    return text.substring(0, i);
  }

  @Contract(pure = true)
  public static @NotNull String substringBeforeLast(@NotNull String text, @NotNull String subString) {
    int i = text.lastIndexOf(subString);
    if (i == -1) return text;
    return text.substring(0, i);
  }

  @Contract(pure = true)
  public static @Nullable String substringAfter(@NotNull String text, @NotNull String subString) {
    int i = text.indexOf(subString);
    if (i == -1) return null;
    return text.substring(i + subString.length());
  }

  @Contract(pure = true)
  public static @Nullable String substringAfterLast(@NotNull String text, @NotNull String subString) {
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

  @Contract(pure = true)
  public static @NotNull String first(@NotNull String text, final int maxLength, final boolean appendEllipsis) {
    return text.length() > maxLength ? text.substring(0, maxLength) + (appendEllipsis ? "..." : "") : text;
  }

  @Contract(pure = true)
  public static @NotNull CharSequence first(@NotNull CharSequence text, final int length, final boolean appendEllipsis) {
    if (text.length() <= length) {
      return text;
    }
    if (appendEllipsis) {
      return text.subSequence(0, length) + "...";
    }
    return text.subSequence(0, length);
  }

  @Contract(pure = true)
  public static @NotNull CharSequence last(@NotNull CharSequence text, final int length, boolean prependEllipsis) {
    if (text.length() <= length) {
      return text;
    }
    if (prependEllipsis) {
      return "..." + text.subSequence(text.length() - length, text.length());
    }
    return text.subSequence(text.length() - length, text.length());
  }

  @Contract(pure = true)
  public static @NotNull String firstLast(@NotNull String text, int length) {
    return text.length() > length
           ? text.subSequence(0, length / 2) + ELLIPSIS + text.subSequence(text.length() - length / 2 - 1, text.length())
           : text;
  }

  @Contract(pure = true)
  public static @NotNull String escapeChar(final @NotNull String str, final char character) {
    return escapeChars(str, character);
  }

  @Contract(pure = true)
  public static @NotNull String escapeChars(final @NotNull String str, final char @NotNull ... character) {
    final StringBuilder buf = new StringBuilder(str);
    for (char c : character) {
      escapeChar(buf, c);
    }
    return buf.toString();
  }

  public static void escapeChar(final @NotNull StringBuilder buf, final char character) {
    int idx = 0;
    while ((idx = indexOf(buf, character, idx)) >= 0) {
      buf.insert(idx, "\\");
      idx += 2;
    }
  }

  @Contract(pure = true)
  public static @NotNull String escapeQuotes(final @NotNull String str) {
    return escapeChar(str, '"');
  }

  public static void escapeQuotes(final @NotNull StringBuilder buf) {
    escapeChar(buf, '"');
  }

  @Contract(pure = true)
  public static @NotNull String escapeSlashes(final @NotNull String str) {
    return escapeChar(str, '/');
  }

  @Contract(pure = true)
  public static @NotNull String escapeBackSlashes(final @NotNull String str) {
    return escapeChar(str, '\\');
  }

  public static void escapeSlashes(final @NotNull StringBuilder buf) {
    escapeChar(buf, '/');
  }

  @Contract(pure = true)
  public static @NotNull String unescapeSlashes(final @NotNull String str) {
    final StringBuilder buf = new StringBuilder(str.length());
    unescapeChar(buf, str, '/');
    return buf.toString();
  }

  @Contract(pure = true)
  public static @NotNull String unescapeBackSlashes(final @NotNull String str) {
    final StringBuilder buf = new StringBuilder(str.length());
    unescapeChar(buf, str, '\\');
    return buf.toString();
  }

  @Contract(pure = true)
  public static @NotNull String unescapeChar(final @NotNull String str, char unescapeChar) {
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
        //noinspection AssignmentToForLoopParameter
        i++;
        ch = str.charAt(i);
        if (ch != unescapeChar) buf.append('\\');
      }

      buf.append(ch);
    }
  }

  public static void quote(final @NotNull StringBuilder builder) {
    quote(builder, '\"');
  }

  public static void quote(final @NotNull StringBuilder builder, final char quotingChar) {
    builder.insert(0, quotingChar);
    builder.append(quotingChar);
  }

  @Contract(pure = true)
  public static @NotNull String wrapWithDoubleQuote(@NotNull String str) {
    return '\"' + str + "\"";
  }

  private static final List<String> REPLACES_REFS = Arrays.asList("&lt;", "&gt;", "&amp;", "&#39;", "&quot;");
  private static final List<String> REPLACES_DISP = Arrays.asList("<", ">", "&", "'", "\"");

  /**
   * @deprecated Use {@link #unescapeXmlEntities(String)} instead
   */
  @Contract(value = "null -> null; !null -> !null",pure = true)
  @Deprecated
  public static String unescapeXml(final @Nullable String text) {
    return text == null ? null : unescapeXmlEntities(text);
  }

  /**
   * @deprecated Use {@link #escapeXmlEntities(String)} instead
   */
  @Contract(value = "null -> null; !null -> !null",pure = true)
  @Deprecated
  public static String escapeXml(final @Nullable String text) {
    return text == null ? null : escapeXmlEntities(text);
  }

  /**
   * @return {@code text} with some standard XML entities replaced with corresponding characters, e.g. '{@code &lt;}' replaced with '<'
   */
  @Contract(pure = true)
  public static @NotNull String unescapeXmlEntities(@NotNull String text) {
    return replace(text, REPLACES_REFS, REPLACES_DISP);
  }

  /**
   * @return {@code text} with some characters replaced with standard XML entities, e.g. '<' replaced with '{@code &lt;}'
   */
  @Contract(pure = true)
  public static @NotNull String escapeXmlEntities(@NotNull String text) {
    return replace(text, REPLACES_DISP, REPLACES_REFS);
  }

  public static @NotNull String removeHtmlTags(@NotNull String htmlString) {
    return removeHtmlTags(htmlString, false);
  }

  public static @NotNull String removeHtmlTags(@NotNull String htmlString, boolean isRemoveStyleTag) {
    if (isEmpty(htmlString)) {
      return "";
    }

    final MyHtml2Text parser = isRemoveStyleTag ? new MyHtml2Text(true) : html2TextParser;
    try {
      parser.parse(new StringReader(htmlString));
    }
    catch (IOException e) {
      Logger.getInstance(StringUtil.class).error(e);
    }
    return parser.getText();
  }

  @Contract(pure = true)
  public static @NotNull String removeEllipsisSuffix(@NotNull String s) {
    String THREE_DOTS = "...";
    if (s.endsWith(THREE_DOTS)) {
      return s.substring(0, s.length() - THREE_DOTS.length());
    }
    if (s.endsWith(ELLIPSIS)) {
      return s.substring(0, s.length() - ELLIPSIS.length());
    }
    return s;
  }

  private static final List<String> MN_QUOTED = Arrays.asList("&&", "__");
  private static final List<String> MN_CHARS = Arrays.asList("&", "_");

  @Contract(pure = true)
  public static @NotNull String escapeMnemonics(@NotNull String text) {
    return replace(text, MN_CHARS, MN_QUOTED);
  }

  @Contract(pure = true)
  public static @NotNull String htmlEmphasize(@NotNull String text) {
    return "<b><code>" + escapeXmlEntities(text) + "</code></b>";
  }


  @Contract(pure = true)
  public static @NotNull String escapeToRegexp(@NotNull String text) {
    final StringBuilder result = new StringBuilder(text.length());
    return escapeToRegexp(text, result).toString();
  }

  public static @NotNull StringBuilder escapeToRegexp(@NotNull CharSequence text, @NotNull StringBuilder builder) {
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
        final Character.UnicodeBlock block = Character.UnicodeBlock.of(c);
        if (block == Character.UnicodeBlock.HIGH_SURROGATES || block == Character.UnicodeBlock.LOW_SURROGATES) {
          builder.append(c);
        } else {
          builder.append('\\').append(c);
        }
      }
    }

    return builder;
  }

  @Contract(pure = true)
  public static boolean isEscapedBackslash(char @NotNull [] chars, int startOffset, int backslashOffset) {
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

  /**
   * @deprecated Use {@link #replace(String, List, List)}
   */
  @Deprecated
  @Contract(pure = true)
  public static @NotNull String replace(@NotNull String text, String @NotNull [] from, String @NotNull [] to) {
    return replace(text, Arrays.asList(from), Arrays.asList(to));
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

  @Contract(pure = true)
  public static String @NotNull [] filterEmptyStrings(String @NotNull [] strings) {
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
    boolean forward = start <= end;
    start = forward ? Math.max(0, start) : Math.min(text.length(), start);
    end = forward ? Math.min(text.length(), end) : Math.max(0, end);
    int count = 0;
    for (int i = forward ? start : start - 1; forward == i < end; i += forward ? 1 : -1) {
      if (text.charAt(i) == c) {
        count++;
      }
      else if (stopAtOtherChar) {
        break;
      }
    }
    return count;
  }

  @Contract(pure = true)
  public static @NotNull String capitalsOnly(@NotNull String s) {
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
  @Contract(pure = true)
  public static @Nullable String joinOrNull(String @NotNull ... args) {
    StringBuilder r = new StringBuilder();
    for (String arg : args) {
      if (arg == null) return null;
      r.append(arg);
    }
    return r.toString();
  }

  @Contract(pure = true)
  public static @Nullable String getPropertyName(@NotNull String methodName) {
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
  @Contract(pure = true)
  public static @NotNull String escapeProperty(@NotNull String input, final boolean isKey) {
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
  public static @NotNull String getQualifiedName(@Nullable String packageName, @NotNull String className) {
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

    String[] part1 = v1.split("[._\\-]");
    String[] part2 = v2.split("[._\\-]");

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

    if (part1.length != part2.length) {
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
    }
    return 0;
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

  @Contract(pure = true)
  public static @NotNull String fixVariableNameDerivedFromPropertyName(@NotNull String name) {
    if (isEmptyOrSpaces(name)) return name;
    char c = name.charAt(0);
    if (isVowel(c)) {
      return "an" + Character.toUpperCase(c) + name.substring(1);
    }
    return "a" + Character.toUpperCase(c) + name.substring(1);
  }

  @Contract(pure = true)
  public static @NotNull String sanitizeJavaIdentifier(@NotNull String name) {
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

  @Contract(pure = true)
  public static @NotNull String tail(@NotNull String s, final int idx) {
    return idx >= s.length() ? "" : s.substring(idx);
  }

  /**
   * Splits string by lines.
   *
   * @param string String to split
   * @return array of strings
   */
  @Contract(pure = true)
  public static String @NotNull [] splitByLines(@NotNull String string) {
    return splitByLines(string, true);
  }

  /**
   * Splits string by lines. If several line separators are in a row corresponding empty lines
   * are also added to result if {@code excludeEmptyStrings} is {@code false}.
   *
   * @param string String to split
   * @return array of strings
   */
  @Contract(pure = true)
  public static String @NotNull [] splitByLines(@NotNull String string, boolean excludeEmptyStrings) {
    return (excludeEmptyStrings ? EOL_SPLIT_PATTERN : EOL_SPLIT_PATTERN_WITH_EMPTY).split(string);
  }

  @Contract(pure = true)
  public static String @NotNull [] splitByLinesDontTrim(@NotNull String string) {
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
  @Contract(pure = true)
  public static String @NotNull [] splitByLinesKeepSeparators(@NotNull String string) {
    return EOL_SPLIT_KEEP_SEPARATORS.split(string);
  }

  @Contract(pure = true)
  public static @NotNull List<Pair<String, Integer>> getWordsWithOffset(@NotNull String s) {
    List<Pair<String, Integer>> res = new ArrayList<>();
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

  @Contract("null -> false")
  public static boolean isNotNegativeNumber(@Nullable CharSequence s) {
    if (s == null) {
      return false;
    }
    for (int i = 0; i < s.length(); i++) {
      if (!isDecimalDigit(s.charAt(i))) {
        return false;
      }
    }
    return true;
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
  public static int compare(@Nullable CharSequence s1, @Nullable CharSequence s2, boolean ignoreCase) {
    if (s1 == s2) return 0;
    if (s1 == null) return -1;
    if (s2 == null) return 1;

    int length1 = s1.length();
    int length2 = s2.length();
    int i = 0;
    for (; i < length1 && i < length2; i++) {
      int diff = compare(s1.charAt(i), s2.charAt(i), ignoreCase);
      if (diff != 0) {
        return diff;
      }
    }
    return length1 - length2;
  }

  @Contract(pure = true)
  public static int comparePairs(@Nullable String s1, @Nullable String t1, @Nullable String s2, @Nullable String t2, boolean ignoreCase) {
    final int compare = compare(s1, s2, ignoreCase);
    return compare != 0 ? compare : compare(t1, t2, ignoreCase);
  }

  @Contract(pure = true)
  public static boolean equals(@Nullable CharSequence s1, @Nullable CharSequence s2) {
    return StringUtilRt.equal(s1, s2, true);
  }

  @Contract(pure = true)
  public static boolean equalsIgnoreCase(@Nullable CharSequence s1, @Nullable CharSequence s2) {
    return StringUtilRt.equal(s1, s2, false);
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

  /**
   * Collapses all white-space (including new lines) between non-white-space characters to a single space character.
   * Leading and trailing white space is removed.
   */
  public static @NotNull String collapseWhiteSpace(@NotNull CharSequence s) {
    final StringBuilder result = new StringBuilder();
    boolean space = false;
    for (int i = 0, length = s.length(); i < length; i++) {
      final char ch = s.charAt(i);
      if (isWhiteSpace(ch)) {
        if (!space) space = true;
      }
      else {
        if (space && result.length() > 0) result.append(' ');
        result.append(ch);
        space = false;
      }
    }
    return result.toString();
  }

  @Contract(pure = true)
  public static boolean findIgnoreCase(@Nullable String toFind, String @NotNull ... where) {
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

  @Contract(pure = true)
  public static @NotNull String formatLinks(@NotNull String message) {
    Pattern linkPattern = Pattern.compile("http://[a-zA-Z0-9./\\-+]+");
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

  @Contract(pure = true)
  public static @NotNull String shortenTextWithEllipsis(final @NotNull String text, final int maxLength, final int suffixLength) {
    return shortenTextWithEllipsis(text, maxLength, suffixLength, false);
  }

  @Contract(pure = true)
  public static @NotNull String trimMiddle(@NotNull String text, int maxLength) {
    return shortenTextWithEllipsis(text, maxLength, maxLength >> 1, true);
  }

  @Contract(pure = true)
  public static @NotNull String shortenTextWithEllipsis(final @NotNull String text,
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

  @Contract(pure = true)
  public static @NotNull String shortenTextWithEllipsis(final @NotNull String text,
                                                        final int maxLength,
                                                        final int suffixLength,
                                                        boolean useEllipsisSymbol) {
    String symbol = useEllipsisSymbol ? ELLIPSIS : "...";
    return shortenTextWithEllipsis(text, maxLength, suffixLength, symbol);
  }

  @Contract(pure = true)
  public static @NotNull String shortenPathWithEllipsis(final @NotNull String path, final int maxLength, boolean useEllipsisSymbol) {
    return shortenTextWithEllipsis(path, maxLength, (int)(maxLength * 0.7), useEllipsisSymbol);
  }

  @Contract(pure = true)
  public static @NotNull String shortenPathWithEllipsis(final @NotNull String path, final int maxLength) {
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
  public static String toUpperCase(String s) {
    return s == null ? null : s.toUpperCase(Locale.ENGLISH);
  }

  @Contract(pure = true)
  public static char toLowerCase(final char a) {
    return StringUtilRt.toLowerCase(a);
  }

  @Contract(pure = true)
  public static boolean isUpperCase(@NotNull CharSequence sequence) {
    for (int i = 0; i < sequence.length(); i++) {
      if (!Character.isUpperCase(sequence.charAt(i))) return false;
    }
    return true;
  }

  public static @Nullable LineSeparator detectSeparators(@NotNull CharSequence text) {
    int index = indexOfAny(text, "\n\r");
    if (index == -1) return null;
    LineSeparator lineSeparator = getLineSeparatorAt(text, index);
    if (lineSeparator == null) {
      throw new AssertionError();
    }
    return lineSeparator;
  }

  public static @Nullable LineSeparator getLineSeparatorAt(@NotNull CharSequence text, int index) {
    if (index < 0 || index >= text.length()) {
      return null;
    }
    char ch = text.charAt(index);
    if (ch == '\r') {
      return index + 1 < text.length() && text.charAt(index + 1) == '\n' ? LineSeparator.CRLF : LineSeparator.CR;
    }
    return ch == '\n' ? LineSeparator.LF : null;
  }

  @Contract(pure = true)
  public static @NotNull String convertLineSeparators(@NotNull String text) {
    return StringUtilRt.convertLineSeparators(text);
  }

  @Contract(pure = true)
  public static @NotNull String convertLineSeparators(@NotNull String text, boolean keepCarriageReturn) {
    return StringUtilRt.convertLineSeparators(text, keepCarriageReturn);
  }

  @Contract(pure = true)
  public static @NotNull String convertLineSeparators(@NotNull String text, @NotNull String newSeparator) {
    return StringUtilRt.convertLineSeparators(text, newSeparator);
  }

  public static @NotNull String convertLineSeparators(@NotNull String text, @NotNull String newSeparator, int @Nullable [] offsetsToKeep) {
    return StringUtilRt.convertLineSeparators(text, newSeparator, offsetsToKeep);
  }

  @Contract(pure = true)
  public static int parseInt(@Nullable String string, int defaultValue) {
    return StringUtilRt.parseInt(string, defaultValue);
  }

  @Contract(pure = true)
  public static long parseLong(@Nullable String string, long defaultValue) {
    return StringUtilRt.parseLong(string, defaultValue);
  }

  @Contract(pure = true)
  public static double parseDouble(@Nullable String string, double defaultValue) {
    return StringUtilRt.parseDouble(string, defaultValue);
  }

  @Contract(pure = true)
  public static <E extends Enum<E>> E parseEnum(@NotNull String string, E defaultValue, @NotNull Class<E> clazz) {
    return StringUtilRt.parseEnum(string, defaultValue, clazz);
  }

  @Contract(pure = true)
  public static @NotNull String getShortName(@NotNull Class<?> aClass) {
    return StringUtilRt.getShortName(aClass);
  }

  @Contract(pure = true)
  public static @NotNull String getShortName(@NotNull String fqName) {
    return StringUtilRt.getShortName(fqName);
  }

  @Contract(pure = true)
  public static @NotNull String getShortName(@NotNull String fqName, char separator) {
    return StringUtilRt.getShortName(fqName, separator);
  }

  /**
   * Equivalent for {@code getShortName(fqName).equals(shortName)}, but could be faster.
   *
   * @param fqName    fully-qualified name (dot-separated)
   * @param shortName a short name, must not contain dots
   * @return true if specified short name is a short name of fully-qualified name
   */
  public static boolean isShortNameOf(@NotNull String fqName, @NotNull String shortName) {
    if (fqName.length() < shortName.length()) return false;
    if (fqName.length() == shortName.length()) return fqName.equals(shortName);
    int diff = fqName.length() - shortName.length();
    if (fqName.charAt(diff - 1) != '.') return false;
    return fqName.regionMatches(diff, shortName, 0, shortName.length());
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

  @Contract(pure = true)
  public static @NotNull CharSequence newBombedCharSequence(@NotNull CharSequence sequence, long delay) {
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
    String s = toLowerCase(string);
    return s.startsWith(toLowerCase(smallPart)) && toLowerCase(bigPart).startsWith(s);
  }

  /**
   * Does the string have an uppercase character?
   * @param s  the string to test.
   * @return   true if the string has an uppercase character, false if not.
   */
  public static boolean hasUpperCaseChar(@NotNull String s) {
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
  public static boolean hasLowerCaseChar(@NotNull String s) {
      char[] chars = s.toCharArray();
      for (char c : chars) {
          if (Character.isLowerCase(c)) {
              return true;
          }
      }
      return false;
  }

  private static final Pattern UNICODE_CHAR = Pattern.compile("\\\\u[0-9a-fA-F]{4}");

  public static String replaceUnicodeEscapeSequences(String text) {
    if (text == null) return null;

    final Matcher matcher = UNICODE_CHAR.matcher(text);
    if (!matcher.find()) return text; // fast path

    matcher.reset();
    int lastEnd = 0;
    final StringBuilder sb = new StringBuilder(text.length());
    while (matcher.find()) {
      sb.append(text, lastEnd, matcher.start());
      final char c = (char)Integer.parseInt(matcher.group().substring(2), 16);
      sb.append(c);
      lastEnd = matcher.end();
    }
    sb.append(text.substring(lastEnd));
    return sb.toString();
  }

  /**
   * Expirable CharSequence. Very useful to control external library execution time,
   * i.e. when java.util.regex.Pattern match goes out of control.
   */
  public abstract static class BombedCharSequence implements CharSequence {
    private final CharSequence delegate;
    private int i;
    private boolean myDefused;

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
      if (myDefused) {
        return;
      }
      if ((++i & 1023) == 0) {
        checkCanceled();
      }
    }

    public final void defuse() {
       myDefused = true;
    }

    @Override
    public @NotNull String toString() {
      check();
      return delegate.toString();
    }

    protected abstract void checkCanceled();

    @Override
    public @NotNull CharSequence subSequence(int i, int i1) {
      check();
      return delegate.subSequence(i, i1);
    }
  }

  @Contract(pure = true)
  public static @NotNull String toHexString(byte @NotNull [] bytes) {
    @SuppressWarnings("SpellCheckingInspection") String digits = "0123456789abcdef";
    StringBuilder sb = new StringBuilder(2 * bytes.length);
    for (byte b : bytes) sb.append(digits.charAt((b >> 4) & 0xf)).append(digits.charAt(b & 0xf));
    return sb.toString();
  }

  @Contract(pure = true)
  public static byte @NotNull [] parseHexString(@NotNull String str) {
    int len = str.length();
    if (len % 2 != 0) throw new IllegalArgumentException("Non-even-length: " + str);
    byte[] bytes = new byte[len / 2];
    for (int i = 0; i < len; i += 2) {
      bytes[i / 2] = (byte)((Character.digit(str.charAt(i), 16) << 4) + Character.digit(str.charAt(i + 1), 16));
    }
    return bytes;
  }

  /** @deprecated use {@link #startsWithConcatenation(String, String...)} */
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2021.1")
  public static boolean startsWithConcatenationOf(@NotNull String string, @NotNull String firstPrefix, @NotNull String secondPrefix) {
    return startsWithConcatenation(string, firstPrefix, secondPrefix);
  }

  /**
   * @return {@code true} if the passed string is not {@code null} and not empty
   * and contains only latin upper- or lower-case characters and digits; {@code false} otherwise.
   */
  @Contract(pure = true)
  public static boolean isLatinAlphanumeric(@Nullable CharSequence str) {
    if (isEmpty(str)) {
      return false;
    }
    for (int i = 0; i < str.length(); i++) {
      char c = str.charAt(i);
      if (c >= 'A' && c <= 'Z' || c >= 'a' && c <= 'z' || Character.isDigit(c)) {
        continue;
      }
      return false;
    }
    return true;
  }

  @Contract(value = "null -> null; !null->!null", pure = true)
  public static String internEmptyString(String s) {
    return s == null ? null : s.isEmpty() ? "" : s;
  }

  /**
   * Finds the next position in the supplied CharSequence which is neither a space nor a tab.
   * @param text text
   * @param pos starting position
   * @return position of the first non-whitespace character after or equal to pos; or the length of the CharSequence
   * if no non-whitespace character found
   */
  public static int skipWhitespaceForward(@NotNull CharSequence text, int pos) {
    int length = text.length();
    while (pos < length && isWhitespaceOrTab(text.charAt(pos))) {
      pos++;
    }
    return pos;
  }

  /**
   * Finds the previous position in the supplied CharSequence which is neither a space nor a tab.
   * @param text text
   * @param pos starting position
   * @return position of the character before or equal to pos which has no space or tab before;
   * or zero if no non-whitespace character found
   */
  public static int skipWhitespaceBackward(@NotNull CharSequence text, int pos) {
    while (pos > 0 && isWhitespaceOrTab(text.charAt(pos - 1))) {
      pos--;
    }
    return pos;
  }

  private static boolean isWhitespaceOrTab(char c) {
    return c == ' ' || c == '\t';
  }
}
